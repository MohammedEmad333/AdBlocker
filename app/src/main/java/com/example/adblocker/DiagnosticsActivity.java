package com.example.adblocker;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Local session diagnostics. No external probe or traffic history export.
 * A running VPN is not proof that third-party encrypted DNS is filtered.
 */
public final class DiagnosticsActivity extends AppCompatActivity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView state, transport, counters, downloads, warning;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            render();
            ui.postDelayed(this, 1500);
        }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.ink));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int margin = dp(20);
        root.setPadding(margin, margin, margin, margin);
        scroll.addView(root);
        setContentView(scroll);
        SystemBarInsets.apply(scroll);

        Button back = new Button(this);
        back.setText(R.string.diagnostics_back);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        addText(root, getString(R.string.diagnostics_title), 24);
        state = addText(root, "", 18);
        transport = addText(root, "", 15);
        counters = addText(root, "", 14);
        downloads = addText(root, "", 14);
        warning = addText(root, getString(R.string.diagnostics_disclaimer), 13);
        Button refresh = new Button(this);
        refresh.setText(R.string.diagnostics_refresh);
        refresh.setOnClickListener(v -> render());
        root.addView(refresh);
        render();
    }

    private TextView addText(LinearLayout root, String value, int sp) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(getColor(R.color.text));
        label.setTextSize(sp);
        label.setPadding(0, dp(10), 0, dp(10));
        root.addView(label);
        return label;
    }

    private void render() {
        DnsHealthSummary.State health = DnsHealthSummary.assess(
                AdBlockVpnService.isRunning, AdBlockVpnService.isStarting,
                AdBlockVpnService.answered.get(), AdBlockVpnService.timeouts.get(),
                AdBlockVpnService.errors.get());
        int resource;
        switch (health) {
            case STARTING: resource = R.string.diagnostics_starting; break;
            case IDLE: resource = R.string.diagnostics_idle; break;
            case HEALTHY: resource = R.string.diagnostics_healthy; break;
            case DEGRADED: resource = R.string.diagnostics_degraded; break;
            default: resource = R.string.diagnostics_stopped;
        }
        state.setText(getString(R.string.diagnostics_state, getString(resource)));
        transport.setText(getString(R.string.diagnostics_upstream,
                AdBlockVpnService.upstreamInfo));
        counters.setText(getString(R.string.diagnostics_counters,
                AdBlockVpnService.queriesSeen.get(),
                AdBlockVpnService.answered.get(),
                AdBlockVpnService.timeouts.get(),
                AdBlockVpnService.errors.get()));
        downloads.setText(getString(R.string.diagnostics_domains,
                AdBlockVpnService.downloadedDomainCount()));
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + .5f);
    }

    @Override protected void onResume() {
        super.onResume();
        ui.removeCallbacks(ticker);
        ui.postDelayed(ticker, 1500);
    }
    @Override protected void onPause() {
        ui.removeCallbacks(ticker);
        super.onPause();
    }
}
