package com.example.adblocker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Simple settings UI with no third-party dependencies. */
public final class SettingsActivity extends AppCompatActivity {
    private FilterSettings settings;
    private LinearLayout root;
    private TextView updateStatus;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTicker = new Runnable() {
        @Override public void run() {
            if (updateStatus != null) refreshTime();
            refreshHandler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new FilterSettings(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.ink));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(32));
        scroll.addView(root);
        setContentView(scroll);
        SystemBarInsets.apply(scroll);

        Button back = button(getString(R.string.settings_back));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        label(getString(R.string.settings_title), 26, R.color.text);
        label(getString(R.string.settings_description), 13, R.color.muted);
        languageSelector();

        section(getString(R.string.settings_sources));
        sourceToggle(getString(R.string.source_stevenblack), FilterSettings.STEVENBLACK);
        sourceToggle(getString(R.string.source_adaway), FilterSettings.ADAWAY);
        updateStatus = label("", 12, R.color.muted);
        refreshTime();
        Button refresh = button(getString(R.string.settings_refresh));
        refresh.setOnClickListener(v -> {
            refresh.setEnabled(false);
            if (AdBlockVpnService.isRunning) {
                settings.setManualRefreshState(FilterSettings.ManualRefreshState.RUNNING);
                sendRefresh(true);
                refreshTime();
                toast(getString(R.string.settings_refresh_requested));
                refresh.setEnabled(true);
            } else {
                toast(getString(R.string.settings_downloading));
                new Thread(() -> {
                    BlocklistRefreshStatus.Outcome outcome;
                    try {
                        outcome = new BlocklistManager(getApplicationContext())
                                .refreshWithStatus().outcome();
                    } catch (RuntimeException e) {
                        android.util.Log.w("AdBlockList", "Manual list refresh failed", e);
                        outcome = BlocklistRefreshStatus.Outcome.FAILED;
                    }
                    final BlocklistRefreshStatus.Outcome result = outcome;
                    runOnUiThread(() -> {
                        refresh.setEnabled(true);
                        refreshTime();
                        int message;
                        switch (result) {
                            case SUCCESS:
                                message = R.string.settings_refresh_finished;
                                break;
                            case PARTIAL:
                                message = R.string.settings_refresh_partial;
                                break;
                            case NO_SOURCES:
                                message = R.string.settings_refresh_disabled;
                                break;
                            default:
                                message = R.string.settings_refresh_failed;
                        }
                        toast(getString(message));
                    });
                }, "ManualFilterDownload").start();
            }
        });
        root.addView(refresh);
        label(getString(R.string.settings_https_hint), 12, R.color.muted);

        domainEditor(getString(R.string.settings_allowed_domains), true);
        domainEditor(getString(R.string.settings_blocked_domains), false);
        installedApps();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!AdBlockVpnService.isRunning) settings.failPendingManualRefresh();
        refreshHandler.removeCallbacks(refreshTicker);
        if (updateStatus != null) refreshTime();
        refreshHandler.postDelayed(refreshTicker, 1500);
    }

    @Override protected void onPause() {
        refreshHandler.removeCallbacks(refreshTicker);
        super.onPause();
    }

    private void refreshTime() {
        long timestamp = settings.lastDownloadedAt();
        String downloaded = timestamp <= 0 ? getString(R.string.settings_no_update)
                : getString(R.string.settings_update_time,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new java.util.Date(timestamp)));
        int outcomeLabel;
        switch (settings.manualRefreshState()) {
            case RUNNING: outcomeLabel = R.string.settings_downloading; break;
            case SUCCESS: outcomeLabel = R.string.settings_refresh_finished; break;
            case PARTIAL: outcomeLabel = R.string.settings_refresh_partial; break;
            case FAILED: outcomeLabel = R.string.settings_refresh_failed; break;
            case NO_SOURCES: outcomeLabel = R.string.settings_refresh_disabled; break;
            default: outcomeLabel = 0;
        }
        updateStatus.setText(outcomeLabel == 0 ? downloaded
                : downloaded + "\\n" + getString(outcomeLabel));
    }

    private void sourceToggle(String title, String id) {
        CheckBox check = new CheckBox(this);
        check.setText(title);
        check.setTextColor(getColor(R.color.text));
        check.setChecked(settings.sourceEnabled(id));
        check.setOnCheckedChangeListener((button, enabled) -> {
            settings.setSourceEnabled(id, enabled);
            sendRefresh(false);
        });
        root.addView(check);
    }

    private void domainEditor(String heading, boolean allow) {
        section(heading);
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        root.addView(area);

        EditText input = new EditText(this);
        input.setHint("example.com");
        input.setHintTextColor(getColor(R.color.muted));
        input.setTextColor(getColor(R.color.text));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        area.addView(input);

        Button add = button(getString(R.string.settings_add_domain));
        area.addView(add);
        LinearLayout entries = new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        area.addView(entries);
        Runnable redraw = () -> showDomains(entries, allow);
        add.setOnClickListener(v -> {
            if (!settings.addDomain(allow, input.getText().toString())) {
                input.setError(getString(R.string.settings_domain_error));
                return;
            }
            input.setText("");
            redraw.run();
            sendRefresh(false);
        });
        redraw.run();
    }

    private void showDomains(LinearLayout entries, boolean allow) {
        entries.removeAllViews();
        List<String> domains = new ArrayList<>(allow
                ? settings.allowedDomains() : settings.blockedDomains());
        Collections.sort(domains);
        if (domains.isEmpty()) {
            TextView empty = small(getString(R.string.settings_no_domains));
            entries.addView(empty);
            return;
        }
        for (String domain : domains) {
            Button remove = button(getString(R.string.settings_remove_domain, domain));
            remove.setAllCaps(false);
            remove.setOnClickListener(v -> {
                settings.removeDomain(allow, domain);
                showDomains(entries, allow);
                sendRefresh(false);
            });
            entries.addView(remove);
        }
    }

    private void installedApps() {
        section(getString(R.string.settings_apps));
        label(getString(R.string.settings_apps_explain), 12, R.color.muted);
        Map<String, String> installed = new TreeMap<>();
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(launcher, 0);
        for (ResolveInfo info : activities) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(getPackageName())) continue;
            CharSequence name = info.loadLabel(pm);
            installed.put(pkg, name == null ? pkg : name.toString());
        }
        Set<String> excluded = settings.excludedPackages();
        if (installed.isEmpty()) label(getString(R.string.settings_no_apps), 12, R.color.muted);
        for (Map.Entry<String, String> entry : installed.entrySet()) {
            String pkg = entry.getKey();
            CheckBox checkbox = new CheckBox(this);
            checkbox.setText(entry.getValue() + " (" + pkg + ")");
            checkbox.setTextColor(getColor(R.color.text));
            checkbox.setTextSize(12);
            checkbox.setChecked(excluded.contains(pkg));
            checkbox.setOnCheckedChangeListener((b, selected) -> {
                settings.setExcluded(pkg, selected);
                toast(getString(R.string.settings_restart_needed));
            });
            root.addView(checkbox);
        }
    }

    private void sendRefresh(boolean force) {
        if (!AdBlockVpnService.isRunning) return;
        Intent intent = new Intent(this, AdBlockVpnService.class)
                .setAction(force ? AdBlockVpnService.ACTION_REFRESH_FORCE
                        : AdBlockVpnService.ACTION_REFRESH);
        startService(intent);
    }

    /** Language selection uses AndroidX AppCompat app locales on Android 8+. */
    private void languageSelector() {
        section(getString(R.string.settings_language));
        Spinner spinner = new Spinner(this);
        String[] names = {
                getString(R.string.language_system),
                getString(R.string.language_arabic),
                getString(R.string.language_english)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String current = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        final int initial = current.startsWith("ar") ? 1 : current.startsWith("en") ? 2 : 0;
        spinner.setSelection(initial);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view,
                                                 int position, long id) {
                String tags = position == 1 ? "ar" : position == 2 ? "en" : "";
                if (tags.equals(AppCompatDelegate.getApplicationLocales().toLanguageTags()))
                    return;
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(spinner);
    }

    private void section(String heading) {
        TextView label = label(heading, 18, R.color.amber);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) label.getLayoutParams();
        params.topMargin = dp(26);
        label.setLayoutParams(params);
    }

    private TextView label(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(getColor(color));
        text.setPadding(0, dp(4), 0, dp(7));
        root.addView(text);
        return text;
    }

    private TextView small(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(getColor(R.color.muted));
        text.setTextSize(12);
        return text;
    }

    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        return b;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
