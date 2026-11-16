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

import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Live, localized protection overview. DNS records stay in process memory only. */
public final class MainActivity extends AppCompatActivity {
    private TextView statusText, upstreamText, stoppedCount, seenCount, allowedCount;
    private TextView blockedRate, streamText, healthText, domainsLoaded, topBlockedText;
    private TextView listFreshnessText, privacyStateText, trendsText;
    private ProgressBar blockingProgress;
    private Button toggleButton, pauseButton;
    private TextView pauseHint;

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
        listFreshnessText = findViewById(R.id.listFreshnessText);
        privacyStateText = findViewById(R.id.privacyStateText);
        trendsText = findViewById(R.id.trendsText);
        topBlockedText = findViewById(R.id.topBlockedText);
        blockingProgress = findViewById(R.id.blockingProgress);
        toggleButton = findViewById(R.id.toggleButton);
        pauseButton = findViewById(R.id.pauseButton);
        pauseHint = findViewById(R.id.pauseHint);

        findViewById(R.id.settingsButton).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.diagnosticsButton).setOnClickListener(v ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));
        findViewById(R.id.openLogButton).setOnClickListener(v ->
                startActivity(new Intent(this, ActivityLogActivity.class)));
        toggleButton.setOnClickListener(v -> toggleProtection());
        pauseButton.setOnClickListener(v -> {
            if (!AdBlockVpnService.isRunning) return;
            if (AdBlockVpnService.protectionPauseRemainingMs() > 0) {
                AdBlockVpnService.resumeProtection();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.pause_protection_title)
                        .setMessage(R.string.pause_protection_warning)
                        .setItems(new String[] {
                                getString(R.string.pause_5_minutes),
                                getString(R.string.pause_15_minutes),
                                getString(R.string.pause_30_minutes)
                        }, (dialog, which) -> {
                            long[] durations = {
                                    TimedProtectionPause.FIVE_MINUTES,
                                    TimedProtectionPause.FIFTEEN_MINUTES,
                                    TimedProtectionPause.THIRTY_MINUTES
                            };
                            AdBlockVpnService.pauseProtection(durations[which]);
                            render();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
            render();
        });
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
        int rate = SessionDashboardState.blockPercentage(blocked, queried);

        long pauseMs = AdBlockVpnService.protectionPauseRemainingMs();
        boolean paused = running && pauseMs > 0;
        statusText.setText(paused ? R.string.protection_paused
                : running ? R.string.protection_on
                : starting ? R.string.protection_starting : R.string.protection_off);
        statusText.setTextColor(getColor(paused ? R.color.amber
                : running ? R.color.text : R.color.muted));
        pauseButton.setVisibility(running ? android.view.View.VISIBLE
                : android.view.View.GONE);
        pauseButton.setText(paused ? R.string.resume_protection
                : R.string.pause_protection);
        pauseHint.setVisibility(paused ? android.view.View.VISIBLE
                : android.view.View.GONE);
        if (paused) {
            long seconds = (pauseMs + 999) / 1000;
            pauseHint.setText(getString(R.string.pause_countdown,
                    (seconds + 59) / 60));
        }
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
        FilterSettings currentSettings = new FilterSettings(this);
        long updated = currentSettings.lastDownloadedAt();
        boolean stale = SessionDashboardState.needsSourceUpdate(
                updated, System.currentTimeMillis());
        listFreshnessText.setText(updated == 0
                ? R.string.dashboard_lists_never_updated
                : stale ? R.string.dashboard_lists_update_due
                : R.string.dashboard_lists_recent);
        if (updated > 0) listFreshnessText.append(" · " +
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(updated)));
        privacyStateText.setText(currentSettings.dnsHistoryEnabled()
                ? R.string.dashboard_history_enabled
                : R.string.dashboard_history_disabled);
        healthText.setText(getString(R.string.health_format,
                AdBlockVpnService.answered.get(),
                AdBlockVpnService.timeouts.get(),
                AdBlockVpnService.errors.get()));
        renderPreview();
        renderTopBlocked();
        renderTrends();
    }

    private void renderTrends() {
        DnsSessionTrends.Snapshot counts = AdBlockVpnService.SESSION_TRENDS.snapshot(
                android.os.SystemClock.elapsedRealtime());
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < DnsSessionTrends.BUCKET_COUNT; i++) {
            if (i > 0) report.append("\n");
            report.append(getString(R.string.dashboard_trends_row,
                    i * 15, (i + 1) * 15, counts.blocked[i], counts.passed[i]));
        }
        trendsText.setText(report);
    }

    private void renderTopBlocked() {
        if (!new FilterSettings(this).dnsHistoryEnabled()) {
            topBlockedText.setText(R.string.dashboard_history_disabled);
            return;
        }
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
        if (!new FilterSettings(this).dnsHistoryEnabled()) {
            streamText.setText(R.string.dashboard_history_disabled);
            streamText.setTextColor(getColor(R.color.muted));
            return;
        }
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
