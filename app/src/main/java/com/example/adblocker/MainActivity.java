package com.example.adblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.List;

/** Live, localized protection overview. DNS records stay in process memory only. */
public final class MainActivity extends AppCompatActivity {
    private TextView statusText, upstreamText, stoppedCount, seenCount, allowedCount;
    private TextView blockedRate, streamText, healthText, domainsLoaded, topBlockedText;
    private ProgressBar blockingProgress;
    private Button toggleButton;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            ui.postDelayed(this, 1500);
        }
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            render();
        }
    };

    private final ActivityResultLauncher<Intent> vpnConsent =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) startVpnService();
                        else Toast.makeText(this, R.string.vpn_permission_needed,
                                Toast.LENGTH_SHORT).show();
                    });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SystemBarInsets.apply(((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        statusText = findViewById(R.id.statusText);
        upstreamText = findViewById(R.id.upstreamText);
        stoppedCount = findViewById(R.id.stoppedCount);
        seenCount = findViewById(R.id.seenCount);
        allowedCount = findViewById(R.id.allowedCount);
        blockedRate = findViewById(R.id.blockedRate);
        streamText = findViewById(R.id.streamText);
        healthText = findViewById(R.id.healthText);
        domainsLoaded = findViewById(R.id.domainsLoaded);
        topBlockedText = findViewById(R.id.topBlockedText);
        blockingProgress = findViewById(R.id.blockingProgress);
        toggleButton = findViewById(R.id.toggleButton);

        findViewById(R.id.settingsButton).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.diagnosticsButton).setOnClickListener(v ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));
        findViewById(R.id.openLogButton).setOnClickListener(v ->
                startActivity(new Intent(this, ActivityLogActivity.class)));
        toggleButton.setOnClickListener(v -> toggleProtection());
        handleQuickTileIntent(getIntent(), savedInstanceState == null);
        render();
    }

    private void toggleProtection() {
        if (AdBlockVpnService.isRunning || AdBlockVpnService.isStarting) stopVpnService();
        else requestConsent();
    }

    /** Process only a fresh user tile tap, not a rotation/recreation. */
    private void handleQuickTileIntent(Intent incoming, boolean fresh) {
        if (incoming == null
                || !AdBlockQuickTile.ACTION_TOGGLE_FROM_TILE.equals(incoming.getAction())) return;
        incoming.setAction(Intent.ACTION_MAIN);
        if (fresh) toggleProtection();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleQuickTileIntent(intent, true);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter events = new IntentFilter();
        events.addAction(AdBlockVpnService.ACTION_VPN_STARTED);
        events.addAction(AdBlockVpnService.ACTION_VPN_STOPPED);
        events.addAction(AdBlockVpnService.ACTION_STATS_UPDATE);
        // Apply non-exported visibility consistently on all supported versions.
        ContextCompat.registerReceiver(this, statusReceiver, events,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        ui.removeCallbacks(tick);
        render();
        ui.postDelayed(tick, 1500);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(tick);
        unregisterReceiver(statusReceiver);
        super.onPause();
    }

    private void requestConsent() {
        Intent consent = VpnService.prepare(this);
        if (consent == null) startVpnService();
        else vpnConsent.launch(consent);
    }

    private void startVpnService() {
        // Request notification permission only after the user starts protection.
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
        Intent start = new Intent(this, AdBlockVpnService.class)
                .setAction(AdBlockVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(start);
        else startService(start);
        statusText.setText(R.string.protection_starting);
    }

    private void stopVpnService() {
        startService(new Intent(this, AdBlockVpnService.class)
                .setAction(AdBlockVpnService.ACTION_STOP));
    }

    private void render() {
        boolean running = AdBlockVpnService.isRunning;
        boolean starting = AdBlockVpnService.isStarting;
        int blocked = Math.max(0, AdBlockVpnService.blockedCount.get());
        int queried = Math.max(0, AdBlockVpnService.queriesSeen.get());
        // Failed, timed-out and still-pending queries are not successful answers.
        int allowed = AdBlockVpnService.QUERY_STATS.successfulAllowedResponses();
        int rate = queried == 0 ? 0 : (int) Math.round(100.0 * blocked / queried);

        statusText.setText(running ? R.string.protection_on
                : starting ? R.string.protection_starting : R.string.protection_off);
        statusText.setTextColor(getColor(running ? R.color.text : R.color.muted));
        upstreamText.setText(running
                ? getString(R.string.resolver_format, AdBlockVpnService.upstreamInfo)
                : getString(starting ? R.string.protection_preparing : R.string.protection_prompt));
        toggleButton.setText(running ? R.string.stop_protection
                : starting ? R.string.cancel_protection : R.string.start_protection);
        toggleButton.setBackgroundResource(running || starting
                ? R.drawable.pill_outline : R.drawable.pill_filled);
        toggleButton.setTextColor(getColor(running || starting ? R.color.text : R.color.ink));

        stoppedCount.setText(String.valueOf(blocked));
        seenCount.setText(String.valueOf(queried));
        allowedCount.setText(String.valueOf(allowed));
        blockedRate.setText(getString(R.string.block_rate_format, rate));
        blockingProgress.setProgress(rate);
        domainsLoaded.setText(getString(R.string.domains_loaded_format,
                AdBlockVpnService.downloadedDomainCount()));
        healthText.setText(getString(R.string.health_format,
                AdBlockVpnService.answered.get(),
                AdBlockVpnService.timeouts.get(),
                AdBlockVpnService.errors.get()));
        renderPreview();
        renderTopBlocked();
    }

    private void renderTopBlocked() {
        List<DnsHistory.DomainCount> top = AdBlockVpnService.HISTORY.topBlocked(3);
        if (top.isEmpty()) {
            topBlockedText.setText(R.string.top_blocked_empty);
            return;
        }
        StringBuilder text = new StringBuilder();
        for (DnsHistory.DomainCount item : top) {
            if (text.length() > 0) text.append("\n");
            text.append(getString(R.string.top_blocked_item, item.domain, item.count));
        }
        topBlockedText.setText(text.toString());
    }

    private void renderPreview() {
        List<DnsHistory.Entry> events = AdBlockVpnService.HISTORY.search(
                "", DnsHistory.Filter.ALL, 6);
        if (events.isEmpty()) {
            streamText.setText(R.string.no_recent_requests);
            streamText.setTextColor(getColor(R.color.muted));
            return;
        }
        SpannableStringBuilder text = new SpannableStringBuilder();
        for (DnsHistory.Entry entry : events) {
            int start = text.length();
            text.append(entry.blocked ? "×  " : "•  ");
            text.append(entry.domain).append("\n");
            text.setSpan(new ForegroundColorSpan(
                    getColor(entry.blocked ? R.color.amber : R.color.muted)),
                    start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        streamText.setText(text);
    }
}
