package com.example.adblocker; // change to match your package

import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AdBlockVpnService extends VpnService {

    private static final String TAG = "AdBlockVpn";

    public static final String ACTION_START        = "com.example.adblocker.START";
    public static final String ACTION_STOP         = "com.example.adblocker.STOP";
    public static final String ACTION_REFRESH      = "com.example.adblocker.REFRESH";
    public static final String ACTION_REFRESH_FORCE = "com.example.adblocker.REFRESH_FORCE";
    public static final String ACTION_VPN_STARTED  = "com.example.adblocker.VPN_STARTED";
    public static final String ACTION_VPN_STOPPED  = "com.example.adblocker.VPN_STOPPED";
    public static final String ACTION_STATS_UPDATE = "com.example.adblocker.STATS_UPDATE";
    public static final String EXTRA_BLOCKED       = "blocked_count";

    private static final String TUN_ADDRESS  = "10.0.0.2";
    private static final String FAKE_DNS     = "10.0.0.1";
    private static final String TUN_ADDRESS_V6 = "fd00::2";
    private static final String FAKE_DNS_V6 = "fd00::1";
    /** Tried only if the network hands us nothing usable. */
    private static final String[] FALLBACK_DNS = {"8.8.8.8", "1.1.1.1", "9.9.9.9", "208.67.222.222"};

    private static final int DNS_TIMEOUT_MS = 4000;
    private static final int POOL_SIZE      = 16; // concurrent DNS lookups
    private static final int MAX_PENDING_DNS = 64; // never queue unbounded traffic

    public static volatile boolean    isRunning    = false;
    public static volatile boolean    isStarting   = false;
    public static final AtomicInteger blockedCount = new AtomicInteger(0);
    public static final DnsQueryStatistics QUERY_STATS = new DnsQueryStatistics();

    // --- live diagnostics, read by MainActivity ---
    public static final AtomicInteger packetsRead = new AtomicInteger(0);
    public static final AtomicInteger queriesSeen = new AtomicInteger(0);
    public static final AtomicInteger forwarded   = new AtomicInteger(0);
    public static final AtomicInteger answered    = new AtomicInteger(0);
    public static final AtomicInteger timeouts    = new AtomicInteger(0);
    public static final AtomicInteger errors      = new AtomicInteger(0);
    public static volatile String upstreamInfo = "-";

    /** Ephemeral and bounded DNS activity, cleared whenever a VPN session starts. */
    public static final DnsHistory HISTORY = new DnsHistory(512);

    private static void note(String domain, boolean blocked) {
        HISTORY.add(domain, blocked, System.currentTimeMillis());
    }

    private static volatile int remoteDomainCount;

    public static int downloadedDomainCount() {
        return remoteDomainCount;
    }

    public static volatile String lastDomain = "-";
    public static volatile String lastError  = "-";

    public static void resetStats() {
        blockedCount.set(0); packetsRead.set(0); queriesSeen.set(0);
        forwarded.set(0); answered.set(0); timeouts.set(0); errors.set(0);
        lastDomain = "-"; lastError = "-";
        HISTORY.clear();
        QUERY_STATS.reset();
        remoteDomainCount = 0;
    }

    private ParcelFileDescriptor tunInterface;
    private volatile Thread      vpnThread;
    private volatile ThreadPoolExecutor dnsPool;
    private volatile ScheduledExecutorService filterScheduler;
    private volatile ScheduledExecutorService tcpScheduler;
    private final DnsTcpTunnel tcpTunnel = new DnsTcpTunnel();
    private final FilterRefreshQueue filterRefreshQueue = new FilterRefreshQueue();
    private volatile DomainRules.Policy filterPolicy = new DomainRules.Policy(
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    private volatile boolean     stopRequested;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicBoolean refreshingResolver = new AtomicBoolean(false);
    private volatile InetAddress upstream;    // the resolver that actually answers
    private volatile Network     underlying;  // the real (non-VPN) network
    private volatile ConnectivityManager.NetworkCallback networkCallback;
    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_REFRESH.equals(action) || ACTION_REFRESH_FORCE.equals(action)) {
            requestFilterRefresh(ACTION_REFRESH_FORCE.equals(action));
            return START_STICKY;
        }
        startFgCompat(NotificationHelper.build(this, 0, true));
        startVpn();
        return START_STICKY;
    }

    private void startFgCompat(android.app.Notification n) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NotificationHelper.NOTIFICATION_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, n);
        }
    }

    @Override
    public void onRevoke() { stopVpn(); stopSelf(); }

    @Override
    public void onDestroy() { stopVpn(); super.onDestroy(); }

    // -------------------------------------------------------------------------
    // VPN thread
    // -------------------------------------------------------------------------

    private synchronized void startVpn() {
        if (vpnThread != null) return;
        stopRequested = false;
        isStarting = true;
        consecutiveFailures.set(0);
        resetStats();
        tcpTunnel.clear();
        vpnThread = new Thread(this::runVpn, "AdBlockVpnThread");
        vpnThread.start();
    }

    private void runVpn() {
        try {
            // Do not block initial VPN startup on slow HTTPS blocklist downloads.
            filterPolicy = readPolicyCached();
            remoteDomainCount = filterPolicy.downloadedDomainCount();
            if (stopRequested) return;
            Log.i(TAG, "Blocklist ready: " + filterPolicy.downloadedDomainCount()
                    + " downloaded domains");
            NotificationHelper.update(this, 0);

            // Do this BEFORE establish(): with no tunnel up yet, these probes
            // go out over the normal network with nothing to interfere.
            underlying = pickUnderlying();
            upstream   = pickUpstream(underlying);
            if (stopRequested) return;
            if (upstream == null) {
                upstreamInfo = "none reachable";
                lastError = "no DNS server answered";
                Log.e(TAG, "No upstream DNS answered -- refusing to start");
                stopSelf();
                return;
            }
            upstreamInfo = upstream.getHostAddress();
            Log.i(TAG, "Upstream DNS: " + upstreamInfo);

            Builder builder = new Builder();
            if (underlying != null) builder.setUnderlyingNetworks(new Network[]{underlying});
            builder.setSession("AdBlocker")
                    .setMtu(1500)
                    .setBlocking(true)  // read() sleeps rather than spinning at 100% CPU
                    .addAddress(TUN_ADDRESS, 32)
                    .addDnsServer(FAKE_DNS)
                    .addRoute(FAKE_DNS, 32)
                    .addAddress(TUN_ADDRESS_V6, 128)
                    .addDnsServer(FAKE_DNS_V6)
                    .addRoute(FAKE_DNS_V6, 128);

            // Android applies this per-app policy when establishing the tunnel.
            // Invalid or uninstalled packages must not prevent all protection.
            for (String excludedPackage : new FilterSettings(this).excludedPackages()) {
                if (getPackageName().equals(excludedPackage)) continue;
                try {
                    builder.addDisallowedApplication(excludedPackage);
                } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Excluded package not installed: " + excludedPackage);
                }
            }

            tunInterface = builder.establish();
            if (stopRequested) return;
            if (tunInterface == null) {
                Log.e(TAG, "establish() returned null -- consent gone");
                stopSelf();
                return;
            }
            Log.i(TAG, "Tunnel up");

            // Bounded queue prevents memory growth on slow or unreachable DNS.
            dnsPool = new ThreadPoolExecutor(POOL_SIZE, POOL_SIZE, 0L,
                    TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAX_PENDING_DNS),
                    new ThreadPoolExecutor.AbortPolicy());

            FileInputStream  in  = new FileInputStream(tunInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(tunInterface.getFileDescriptor());
            if (stopRequested) return;
            isRunning = true;
            isStarting = false;
            watchNetworks();
            // Check every 30 minutes; per-source cache freshness enforces daily updates.
            filterScheduler = Executors.newSingleThreadScheduledExecutor();
            filterScheduler.scheduleWithFixedDelay(() -> requestFilterRefresh(false),
                    30, 30, TimeUnit.MINUTES);
            // Download expired or missing lists only after the tunnel is ready.
            // The refresh worker uses protected/bound sockets as appropriate.
            requestFilterRefresh(false);
            tcpScheduler = Executors.newSingleThreadScheduledExecutor();
            tcpScheduler.scheduleWithFixedDelay(() -> {
                if (stopRequested) return;
                try {
                    writeTcpPackets(out, tcpTunnel.tick(System.currentTimeMillis()));
                } catch (IOException e) {
                    if (!stopRequested) Log.w(TAG, "TCP response timer failed", e);
                }
            }, 1, 1, TimeUnit.SECONDS);
            broadcast(ACTION_VPN_STARTED);

            byte[] buf = new byte[32767];
            while (!stopRequested && !Thread.currentThread().isInterrupted()) {
                int len = in.read(buf);
                if (stopRequested) break;
                if (len > 0) { packetsRead.incrementAndGet(); handlePacket(buf, len, out); }
            }
        } catch (Exception e) {
            if (!stopRequested) {
                lastError = String.valueOf(e.getMessage());
                Log.w(TAG, "VPN loop ended unexpectedly", e);
            }
        } finally {
            stopRequested = true;
            unwatchNetworks();
            if (dnsPool != null) dnsPool.shutdownNow();
            if (filterScheduler != null) filterScheduler.shutdownNow();
            filterRefreshQueue.reset();
            if (tcpScheduler != null) tcpScheduler.shutdownNow();
            tcpTunnel.clear();
            closeTun();
            isRunning = false;
            isStarting = false;
            synchronized (this) {
                if (vpnThread == Thread.currentThread()) vpnThread = null;
            }
            stopForeground(true);
            broadcast(ACTION_VPN_STOPPED);
            stopSelf();
        }
    }

    // -------------------------------------------------------------------------
    // Packet handling
    // -------------------------------------------------------------------------

    private void handlePacket(byte[] packet, int bytesRead, FileOutputStream out) {
        try {
            if (stopRequested || bytesRead < 20) return;
            int family = (packet[0] >>> 4) & 15;
            if ((family == 4 && (packet[9] & 255) == 6)
                    || (family == 6 && bytesRead >= 40 && (packet[6] & 255) == 6)) {
                handleTcpPacket(packet, bytesRead, out);
                return;
            }
            DnsTunnelPacket.Query query = DnsTunnelPacket.parse(packet, bytesRead);
            if (query == null) return;
            String domain = DnsPacket.extractDomain(packet, query.dnsOffset, query.dnsLength);
            if (domain == null) return;
            queriesSeen.incrementAndGet();
            lastDomain = domain;

            boolean blocked = isBlocked(domain);
            note(domain, blocked);
            if (blocked) {
                int count = blockedCount.incrementAndGet();
                if (count % 20 == 0) NotificationHelper.update(this, count);
                byte[] response = DnsPacket.buildNxDomain(packet, query.dnsOffset, query.dnsLength);
                byte[] reply = DnsTunnelPacket.wrapReply(packet, query, response);
                writeReply(out, reply);
                Intent update = new Intent(ACTION_STATS_UPDATE).setPackage(getPackageName());
                update.putExtra(EXTRA_BLOCKED, count);
                sendBroadcast(update);
                return;
            }

            // The TUN input buffer is reused immediately on the next read.
            byte[] copy = Arrays.copyOf(packet, query.packetLength);
            if (dnsPool == null) return;
            forwarded.incrementAndGet();
            try {
                dnsPool.execute(() -> forwardAndReply(copy, query, out));
            } catch (RejectedExecutionException saturated) {
                errors.incrementAndGet();
                sendServFail(copy, query, out);
            }
        } catch (Exception e) {
            errors.incrementAndGet();
            lastError = String.valueOf(e.getMessage());
            Log.w(TAG, "Dropped DNS packet: " + e.getMessage());
        }
    }

    private void writeReply(FileOutputStream out, byte[] bytes) throws IOException {
        synchronized (out) {
            if (!stopRequested) out.write(bytes);
        }
    }

    private void sendServFail(byte[] original, DnsTunnelPacket.Query query,
                              FileOutputStream out) {
        try {
            byte[] dns = DnsPacket.buildServFail(original, query.dnsOffset, query.dnsLength);
            writeReply(out, DnsTunnelPacket.wrapReply(original, query, dns));
        } catch (IOException e) {
            if (!stopRequested) Log.w(TAG, "Cannot reply SERVFAIL", e);
        }
    }

    /**
     * DNS/TCP packets use a small purpose-built stream endpoint; regular
     * non-DNS TCP traffic is never routed into this VPN.
     */
    private void handleTcpPacket(byte[] packet, int size, FileOutputStream out) throws IOException {
        DnsTcpTunnel.Result result = tcpTunnel.receive(packet, size, System.currentTimeMillis());
        writeTcpPackets(out, result.replies);
        DnsTcpTunnel.Request request = result.request;
        if (request == null) return;

        String domain = DnsPacket.extractDomain(request.dns, 0, request.dns.length);
        if (domain == null) return; // already validated by the TCP stream parser
        queriesSeen.incrementAndGet();
        lastDomain = domain;
        boolean blocked = isBlocked(domain);
        note(domain, blocked);
        if (blocked) {
            int count = blockedCount.incrementAndGet();
            if (count % 20 == 0) NotificationHelper.update(this, count);
            answerTcpRequest(request,
                    DnsPacket.buildNxDomain(request.dns, 0, request.dns.length), out);
            broadcast(ACTION_STATS_UPDATE);
            return;
        }
        forwarded.incrementAndGet();
        ThreadPoolExecutor pool = dnsPool;
        if (pool == null) {
            answerTcpRequest(request,
                    DnsPacket.buildServFail(request.dns, 0, request.dns.length), out);
            return;
        }
        try {
            pool.execute(() -> resolveTcpDns(request, out));
        } catch (RejectedExecutionException busy) {
            errors.incrementAndGet();
            answerTcpRequest(request,
                    DnsPacket.buildServFail(request.dns, 0, request.dns.length), out);
        }
    }

    private void writeTcpPackets(FileOutputStream out, List<byte[]> packets) throws IOException {
        for (byte[] packet : packets) writeReply(out, packet);
    }

    private boolean answerTcpRequest(DnsTcpTunnel.Request request, byte[] dns,
                                  FileOutputStream out) throws IOException {
        DnsTcpTunnel.Result result = tcpTunnel.complete(request, dns,
                System.currentTimeMillis());
        writeTcpPackets(out, result.replies);
        return !result.replies.isEmpty();
    }

    /** Native TCP/53 first; fallback UDP/53 if the upstream blocks TCP. */
    private void resolveTcpDns(DnsTcpTunnel.Request request, FileOutputStream out) {
        if (stopRequested) return;
        byte[] response = null;
        InetAddress server = upstream;
        Network network = underlying;
        try {
            if (server != null) {
                response = retryTruncatedOverTcp(request.dns, server, network);
                if (response == null) response = resolveDnsViaUdp(request.dns, server, network);
            }
            if (response == null) {
                errors.incrementAndGet();
                recordUpstreamFailure();
                response = DnsPacket.buildServFail(request.dns, 0, request.dns.length);
            } else {
                answered.incrementAndGet();
                consecutiveFailures.set(0);
            }
            if (!stopRequested && answerTcpRequest(request, response, out)
                    && (response[3] & 15) != 2) {
                QUERY_STATS.recordAllowedResponse();
            }
        } catch (IOException e) {
            if (!stopRequested) {
                errors.incrementAndGet();
                Log.w(TAG, "TCP DNS tunnel reply failed", e);
            }
        }
    }

    private byte[] resolveDnsViaUdp(byte[] dns, InetAddress server, Network network) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            bindOutside(socket, network);
            socket.connect(server, 53);
            socket.setSoTimeout(DNS_TIMEOUT_MS);
            socket.send(new DatagramPacket(dns, dns.length));
            byte[] received = new byte[DnsTcpWire.MAX_DNS_RESPONSE];
            DatagramPacket reply = new DatagramPacket(received, received.length);
            socket.receive(reply);
            if (reply.getLength() < 12
                    || !DnsTcpWire.matchesQuery(dns, received, reply.getLength())
                    || (received[2] & 0x02) != 0) return null;
            return Arrays.copyOf(received, reply.getLength());
        } catch (SocketTimeoutException e) {
            timeouts.incrementAndGet();
            return null;
        } catch (IOException e) {
            Log.w(TAG, "Fallback UDP DNS failed: " + e.getMessage());
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }

    private boolean isBlocked(String domain) {
        return filterPolicy.shouldBlock(domain);
    }

    private DomainRules.Policy readPolicy(boolean forceDownload) {
        FilterSettings settings = new FilterSettings(this);
        Set<String> remote = new BlocklistManager(this).load(forceDownload);
        return new DomainRules.Policy(settings.allowedDomains(),
                settings.blockedDomains(), remote);
    }

    private DomainRules.Policy readPolicyCached() {
        FilterSettings settings = new FilterSettings(this);
        Set<String> remote = new BlocklistManager(this).loadCached();
        return new DomainRules.Policy(settings.allowedDomains(),
                settings.blockedDomains(), remote);
    }

    /** Coalesce concurrent changes, retaining a forced refresh while a reload runs. */
    private void requestFilterRefresh(boolean force) {
        ScheduledExecutorService scheduler = filterScheduler;
        if (stopRequested || scheduler == null || scheduler.isShutdown()) return;
        if (!filterRefreshQueue.request(force)) return;
        try {
            scheduler.execute(() -> {
                try {
                    Boolean next;
                    while (!stopRequested && (next = filterRefreshQueue.next()) != null) {
                        try {
                            DomainRules.Policy updated = readPolicy(next);
                            if (!stopRequested) {
                                filterPolicy = updated;
                                remoteDomainCount = updated.downloadedDomainCount();
                                Log.i(TAG, "Filter policy refreshed: "
                                        + updated.downloadedDomainCount() + " remote domains");
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Filter refresh failed; keeping previous policy", e);
                        }
                    }
                } finally {
                    // On cancellation/shutdown the next VPN session starts cleanly.
                    if (stopRequested) filterRefreshQueue.reset();
                }
            });
        } catch (RejectedExecutionException e) {
            filterRefreshQueue.reset();
        }
    }

    /**
     * Forward one query upstream and relay the answer back.
     *
     * Each query gets its OWN socket. The previous design shared a single
     * socket across every query: once any lookup timed out, its late answer
     * was handed back as the NEXT query's answer, and every lookup after that
     * stayed one behind -- DNS died permanently until the VPN restarted.
     * A private socket per query makes that impossible, and the transaction-ID
     * check below rejects any stray packet regardless.
     */
    private void forwardAndReply(byte[] packet, DnsTunnelPacket.Query parsed,
                                 FileOutputStream out) {
        DatagramSocket sock = null;
        try {
            if (stopRequested) return;
            InetAddress resolver = upstream;
            Network network = underlying;
            if (resolver == null) {
                errors.incrementAndGet();
                sendServFail(packet, parsed, out);
                requestResolverRefresh(null);
                return;
            }
            sock = new DatagramSocket();
            bindOutside(sock, network);
            sock.connect(resolver, 53);
            sock.setSoTimeout(DNS_TIMEOUT_MS);

            byte[] query = Arrays.copyOfRange(packet, parsed.dnsOffset,
                    parsed.dnsOffset + parsed.dnsLength);
            sock.send(new DatagramPacket(query, query.length));
            byte[] buf = new byte[4096];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            sock.receive(resp);
            if (resp.getLength() < 12 || !DnsTcpWire.matchesQuery(query, buf, resp.getLength())) {
                throw new IOException("Invalid DNS transaction or response");
            }

            byte[] dnsResponse = Arrays.copyOf(buf, resp.getLength());
            // UDP TC=1 indicates truncation. Retry over protected TCP/53 to
            // obtain the complete response, still replying via UDP to the app.
            if ((dnsResponse[2] & 0x02) != 0) {
                byte[] full = retryTruncatedOverTcp(query, resolver, network);
                // The tunnel MTU is 1500 bytes; avoid oversized IPv4/IPv6 replies.
                if (full != null && full.length <= 1500 - parsed.ipHeaderLength - 8) {
                    dnsResponse = full;
                }
            }
            if (dnsResponse.length > 1500 - parsed.ipHeaderLength - 8) {
                errors.incrementAndGet();
                sendServFail(packet, parsed, out);
                return;
            }
            writeReply(out, DnsTunnelPacket.wrapReply(packet, parsed, dnsResponse));
            if ((dnsResponse[3] & 15) != 2) QUERY_STATS.recordAllowedResponse();
            answered.incrementAndGet();
            consecutiveFailures.set(0);
        } catch (SocketTimeoutException e) {
            timeouts.incrementAndGet();
            recordUpstreamFailure();
            sendServFail(packet, parsed, out);
        } catch (Exception e) {
            if (!stopRequested) {
                errors.incrementAndGet();
                lastError = String.valueOf(e.getMessage());
                recordUpstreamFailure();
                sendServFail(packet, parsed, out);
                Log.w(TAG, "DNS relay failed: " + e.getMessage());
            }
        } finally {
            if (sock != null) sock.close();
        }
    }

    /** Upstream TCP fallback, not a TCP proxy for apps sending DNS/TCP. */
    private byte[] retryTruncatedOverTcp(byte[] query, InetAddress server, Network net) {
        try (Socket socket = new Socket()) {
            protect(socket);
            if (net != null) net.bindSocket(socket);
            socket.connect(new InetSocketAddress(server, 53), DNS_TIMEOUT_MS);
            socket.setSoTimeout(DNS_TIMEOUT_MS);
            DnsTcpWire.writeQuery(socket.getOutputStream(), query);
            byte[] response = DnsTcpWire.readResponse(socket.getInputStream());
            return DnsTcpWire.matchesQuery(query, response) ? response : null;
        } catch (Exception e) {
            Log.w(TAG, "DNS TCP fallback unavailable: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Keep a socket off our own tunnel. protect() marks it to bypass the VPN;
     * binding it to the underlying network is the stronger guarantee, since
     * our own app is otherwise placed on the VPN network like everyone else.
     */
    private void bindOutside(DatagramSocket sock, Network network) {
        protect(sock);
        if (network != null) {
            try { network.bindSocket(sock); } catch (Exception ignored) {}
        }
    }

    /** Track underlying network changes without rebuilding or blocking the TUN loop. */
    private void watchNetworks() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) return;
        ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                requestResolverRefresh(network);
            }
            @Override public void onLost(Network network) {
                if (network.equals(underlying)) requestResolverRefresh(null);
            }
            @Override public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                if (network.equals(underlying)) requestResolverRefresh(network);
            }
        };
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(request, callback);
            networkCallback = callback;
        } catch (RuntimeException e) {
            Log.w(TAG, "Network callbacks unavailable: " + e.getMessage());
        }
    }

    private void unwatchNetworks() {
        ConnectivityManager.NetworkCallback callback = networkCallback;
        networkCallback = null;
        if (callback == null) return;
        try {
            getSystemService(ConnectivityManager.class).unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {}
    }

    private boolean usableNetwork(Network candidate) {
        if (candidate == null) return false;
        try {
            NetworkCapabilities capabilities = getSystemService(ConnectivityManager.class)
                    .getNetworkCapabilities(candidate);
            return capabilities != null
                    && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void recordUpstreamFailure() {
        if (stopRequested || consecutiveFailures.incrementAndGet() < 3) return;
        requestResolverRefresh(null);
    }

    /** One guarded worker: the callback never does synchronous network I/O. */
    private void requestResolverRefresh(Network preferred) {
        if (stopRequested || !isRunning
                || !refreshingResolver.compareAndSet(false, true)) return;
        Thread recovery = new Thread(() -> {
            try {
                Network candidate = usableNetwork(preferred) ? preferred : pickUnderlying();
                InetAddress dns = candidate == null ? null : pickUpstream(candidate);
                if (stopRequested) return;
                if (dns != null) {
                    underlying = candidate;
                    upstream = dns;
                    upstreamInfo = dns.getHostAddress();
                    consecutiveFailures.set(0);
                    try {
                        setUnderlyingNetworks(new Network[]{candidate});
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Could not update underlying network", e);
                    }
                } else if (!usableNetwork(underlying)) {
                    underlying = null;
                    upstream = null;
                    upstreamInfo = "waiting for network";
                }
            } finally {
                refreshingResolver.set(false);
            }
        }, "AdBlockDnsRecovery");
        recovery.setDaemon(true);
        recovery.start();
    }

    /** The real internet-capable network behind us -- wifi or cellular, never a VPN. */
    private Network pickUnderlying() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            Network active = cm.getActiveNetwork();
            if (usableNetwork(active)) return active;
            // Preserve the existing transport when it is still connected.
            if (usableNetwork(underlying)) return underlying;
            for (Network n : cm.getAllNetworks()) {
                if (usableNetwork(n)) return n;
            }
        } catch (Exception e) {
            Log.w(TAG, "pickUnderlying: " + e.getMessage());
        }
        return null;
    }

    /**
     * Find a resolver that actually answers on THIS network.
     *
     * The network's own DHCP-provided servers come first: many ISPs drop
     * traffic to outside resolvers, which is exactly what killed the previous
     * build -- it hardcoded 8.8.8.8 and every single lookup timed out.
     * Public resolvers are only a fallback.
     */
    private InetAddress pickUpstream(Network net) {
        List<InetAddress> candidates = new ArrayList<>();

        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            if (net != null) {
                LinkProperties lp = cm.getLinkProperties(net);
                if (lp != null) {
                    for (InetAddress a : lp.getDnsServers()) {
                        if (!candidates.contains(a)) candidates.add(a);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "reading system DNS: " + e.getMessage());
        }

        for (String f : FALLBACK_DNS) {
            try {
                InetAddress a = InetAddress.getByName(f);
                if (!candidates.contains(a)) candidates.add(a);
            } catch (Exception ignored) {}
        }

        final long startedNanos = System.nanoTime();
        for (InetAddress c : candidates) {
            if (stopRequested || Thread.currentThread().isInterrupted()) return null;
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedNanos);
            int timeoutMs = DnsProbeBudget.timeoutMillis(elapsedMs);
            if (timeoutMs == 0) {
                Log.w(TAG, "DNS resolver discovery budget exhausted");
                break;
            }
            if (answers(c, net, timeoutMs)) {
                Log.i(TAG, "upstream chosen: " + c.getHostAddress());
                return c;
            }
            Log.w(TAG, "no answer from " + c.getHostAddress());
        }
        return null;
    }

    /** One real lookup against a candidate resolver. */
    private boolean answers(InetAddress server, Network net, int timeoutMs) {
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            protect(s);
            if (net != null) { try { net.bindSocket(s); } catch (Exception ignored) {} }
            s.setSoTimeout(timeoutMs);

            byte[] q = DnsPacket.buildQuery("example.com", 0x4A21);
            s.connect(server, 53);
            s.send(new DatagramPacket(q, q.length));

            byte[] buf = new byte[512];
            DatagramPacket r = new DatagramPacket(buf, buf.length);
            s.receive(r);
            return DnsResolverProbe.accepts(q, buf, r.getLength());
        } catch (Exception e) {
            return false;
        } finally {
            if (s != null) s.close();
        }
    }

    private void broadcast(String action) {
        sendBroadcast(new Intent(action).setPackage(getPackageName()));
    }

    private void stopVpn() {
        stopRequested = true;
        Thread t = vpnThread;
        if (t != null) t.interrupt();
        closeTun();
    }

    private void closeTun() {
        ParcelFileDescriptor pfd = tunInterface;
        tunInterface = null;
        if (pfd != null) {
            try { pfd.close(); } catch (Exception ignored) {}
        }
    }
}
