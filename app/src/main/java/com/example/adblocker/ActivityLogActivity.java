package com.example.adblocker;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * Searchable local DNS activity. The service retains at most 512 lookups.
 * No DNS history is persisted, and the UI does not capture traffic itself.
 */
public final class ActivityLogActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            render(false);
            handler.postDelayed(this, 1500);
        }
    };
    private LinearLayout results;
    private TextView summary;
    private android.widget.EditText search;
    private RadioGroup filters;
    private long renderedVersion = -1;
    private String renderedQuery = "";
    private DnsHistory.Filter renderedFilter = null;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_log);
        SystemBarInsets.apply(findViewById(android.R.id.content).getChildAt(0));
        findViewById(R.id.logBackButton).setOnClickListener(v -> finish());
        search = findViewById(R.id.domainSearch);
        filters = findViewById(R.id.logFilters);
        summary = findViewById(R.id.logSummary);
        results = findViewById(R.id.logEntries);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                render(true);
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        filters.setOnCheckedChangeListener((group, checkedId) -> render(true));
        render(true);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        render(true);
        handler.postDelayed(ticker, 1500);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private DnsHistory.Filter filter() {
        int selected = filters.getCheckedRadioButtonId();
        if (selected == R.id.filterBlocked) return DnsHistory.Filter.BLOCKED;
        if (selected == R.id.filterAllowed) return DnsHistory.Filter.ALLOWED;
        return DnsHistory.Filter.ALL;
    }

    private void render(boolean force) {
        if (results == null) return;
        String query = search.getText().toString().trim();
        DnsHistory.Filter mode = filter();
        long version = AdBlockVpnService.HISTORY.version();
        if (!force && version == renderedVersion && query.equals(renderedQuery)
                && mode == renderedFilter) return;
        renderedVersion = version;
        renderedQuery = query;
        renderedFilter = mode;

        List<DnsHistory.Entry> rows = AdBlockVpnService.HISTORY.search(query, mode, 150);
        summary.setText(getString(R.string.log_count_format, rows.size(),
                AdBlockVpnService.HISTORY.size()));
        results.removeAllViews();
        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.log_empty);
            empty.setTextColor(getColor(R.color.muted));
            empty.setTextSize(15);
            empty.setPadding(0, dp(18), 0, dp(18));
            results.addView(empty);
            return;
        }
        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
        for (DnsHistory.Entry row : rows) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setBackgroundResource(R.drawable.panel);
            item.setPadding(dp(16), dp(10), dp(16), dp(10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            results.addView(item, params);

            TextView domain = new TextView(this);
            domain.setText(row.domain);
            domain.setTextColor(getColor(R.color.text));
            domain.setTextSize(15);
            domain.setTextDirection(View.TEXT_DIRECTION_LTR);
            domain.setTextIsSelectable(true);
            item.addView(domain);

            TextView detail = new TextView(this);
            detail.setText(getString(row.blocked ? R.string.log_blocked : R.string.log_allowed)
                    + " · " + timeFormat.format(new Date(row.timeMillis)));
            detail.setTextColor(getColor(row.blocked ? R.color.amber : R.color.muted));
            detail.setTextSize(12);
            detail.setPadding(0, dp(5), 0, 0);
            item.addView(detail);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
