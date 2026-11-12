package com.example.adblocker; // change to match your app's package name

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Downloads, caches and parses the blocklist.
 *
 * Call load() from a background thread only -- it may hit the network.
 */
public class BlocklistManager {

    private static final String TAG = "AdBlockList";

    // StevenBlack's unified hosts list: ~130k ad/tracking/malware domains.
    // Only curated HTTPS hosts sources; optional AdAway defaults off.
    private static final String STEVENBLACK_URL =
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    private static final String ADAWAY_URL =
            "https://raw.githubusercontent.com/AdAway/adaway.github.io/master/hosts.txt";

    private static final String STEVENBLACK_CACHE = "blocklist.txt"; // migrate old installs in place
    private static final String ADAWAY_CACHE = "blocklist-adaway.txt";
    private static final long MAX_CACHE_AGE_MS = 24L * 60 * 60 * 1000; // daily refresh

    private static final long MAX_BLOCKLIST_BYTES = 16L * 1024 * 1024;

    // Hostnames that appear in hosts files but must never be blocked.
    private static final Set<String> IGNORED = new HashSet<>(Arrays.asList(
            "localhost", "localhost.localdomain", "local", "broadcasthost",
            "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
            "ip6-allnodes", "ip6-allrouters", "ip6-allhosts"
    ));

    private final Context context;

    public BlocklistManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Freshness ladder:
     *   fresh cache      -> use it
     *   stale / no cache -> try to download a new copy
     *   download failed  -> use the stale cache if one exists
     *   nothing at all   -> tiny built-in list so blocking still works
     */
    public Set<String> load() {
        return load(false);
    }

    /** Refresh only expired sources unless forced. An offline cache is retained. */
    public Set<String> load(boolean force) {
        return loadInternal(force, false, null);
    }

    /** Force an update and report source results, including cached fallbacks. */
    public BlocklistRefreshStatus refreshWithStatus() {
        BlocklistRefreshStatus status = new BlocklistRefreshStatus();
        loadInternal(true, false, status);
        return status;
    }

    /** Startup-safe loading: use local cache only, never attempt HTTP. */
    public Set<String> loadCached() {
        return loadInternal(false, true, null);
    }

    private Set<String> loadInternal(boolean force, boolean cacheOnly,
                                     BlocklistRefreshStatus status) {
        FilterSettings settings = new FilterSettings(context);
        Set<String> combined = new HashSet<>(200_000);
        boolean anyEnabled = false;

        if (settings.sourceEnabled(FilterSettings.STEVENBLACK)) {
            anyEnabled = true;
            combined.addAll(loadSource(STEVENBLACK_URL, STEVENBLACK_CACHE, force, cacheOnly, settings, status));
        }
        if (settings.sourceEnabled(FilterSettings.ADAWAY)) {
            anyEnabled = true;
            combined.addAll(loadSource(ADAWAY_URL, ADAWAY_CACHE, force, cacheOnly, settings, status));
        }

        // An explicit choice to disable all remote sources must not be overridden.
        if (!anyEnabled) return combined;
        if (!combined.isEmpty()) return combined;

        Log.w(TAG, "No remote lists available: using the small built-in safety list");
        return new HashSet<>(Arrays.asList(
                "doubleclick.net", "googlesyndication.com", "google-analytics.com"));
    }

    private Set<String> loadSource(String sourceUrl, String fileName, boolean force,
                                   boolean cacheOnly, FilterSettings settings,
                                   BlocklistRefreshStatus status) {
        File cache = new File(context.getFilesDir(), fileName);
        boolean fresh = cache.exists()
                && cache.length() > 1024
                && System.currentTimeMillis() - cache.lastModified() < MAX_CACHE_AGE_MS;
        if (!cacheOnly && (force || !fresh)) {
            try {
                download(sourceUrl, cache);
                settings.markDownloaded();
                if (status != null) status.recordSuccess();
            } catch (IOException e) {
                if (status != null) status.recordFailure();
                Log.w(TAG, "Failed to update " + fileName + ": " + e.getMessage());
            }
        }

        if (cache.exists() && cache.length() <= MAX_BLOCKLIST_BYTES) {
            try (InputStream in = new FileInputStream(cache)) {
                Set<String> parsed = parse(in);
                if (!parsed.isEmpty()) {
                    Log.i(TAG, "Loaded " + parsed.size() + " domains from " + fileName);
                    return parsed;
                }
            } catch (IOException e) {
                Log.w(TAG, "Cache read failed for " + fileName + ": " + e.getMessage());
            }
        }
        return new HashSet<>();
    }

    private void download(String sourceUrl, File cache) throws IOException {
        Log.i(TAG, "Downloading source " + sourceUrl);
        File tmp = new File(cache.getPath() + ".tmp");
        HttpURLConnection conn = (HttpURLConnection) new URL(sourceUrl).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Blocklist HTTP status: " + conn.getResponseCode());
            }
            if (conn.getContentLengthLong() > MAX_BLOCKLIST_BYTES)
                throw new IOException("Blocklist exceeds safe download limit");
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                long downloaded = 0;
                while ((n = in.read(buf)) != -1) {
                    downloaded += n;
                    if (downloaded > MAX_BLOCKLIST_BYTES)
                        throw new IOException("Blocklist exceeds safe download limit");
                    out.write(buf, 0, n);
                }
            }
            if (tmp.length() < 1024) throw new IOException("Blocklist download too small");
            try (InputStream check = new FileInputStream(tmp)) {
                if (parse(check).size() < 1000) {
                    throw new IOException("Blocklist download did not contain enough domains");
                }
            }
            try {
                Files.move(tmp.toPath(), cache.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Log.i(TAG, "Blocklist saved (" + cache.length() / 1024 + " KB)");
        } finally {
            conn.disconnect();
            if (tmp.exists() && !tmp.delete()) Log.w(TAG, "Could not delete temporary blocklist");
        }
    }

    /**
     * Handles both formats:
     *   hosts:  "0.0.0.0 ads.example.com"   (StevenBlack style)
     *   plain:  "ads.example.com"           (OISD style, one domain per line)
     */
    private Set<String> parse(InputStream in) throws IOException {
        Set<String> domains = new HashSet<>(200_000); // avoid rehashing mid-load
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);

        String line;
        while ((line = reader.readLine()) != null) {
            int hash = line.indexOf('#');
            if (hash != -1) {
                line = line.substring(0, hash); // strip comments, inline ones too
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] tokens = line.split("\\s+");
            // More than one token -> hosts format, skip the IP column.
            int first = tokens.length > 1 ? 1 : 0;

            for (int i = first; i < tokens.length; i++) {
                String d = DomainRules.normalize(tokens[i]);
                if (d == null || IGNORED.contains(d) || looksLikeIp(d)) continue;
                domains.add(d);
            }
        }
        return domains;
    }

    /** True for tokens made only of digits, dots and colons (bare IPs). */
    private static boolean looksLikeIp(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && c != '.' && c != ':') {
                return false; // contains letters -> a real domain name
            }
        }
        return true;
    }
}
