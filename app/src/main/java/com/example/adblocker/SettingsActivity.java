package com.example.adblocker;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
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
public final class SettingsActivity extends Activity {
    private FilterSettings settings;
    private LinearLayout root;
    private TextView updateStatus;

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

        Button back = button("Back to protection");
        back.setOnClickListener(v -> finish());
        root.addView(back);
        label("FILTER SETTINGS", 26, R.color.text);
        label("Changes to lists apply while protection is running. App exclusions need a stop/start of VPN.", 13, R.color.muted);

        section("Blocklist sources");
        sourceToggle("StevenBlack (default)", FilterSettings.STEVENBLACK);
        sourceToggle("AdAway (optional)", FilterSettings.ADAWAY);
        updateStatus = label("", 12, R.color.muted);
        refreshTime();
        Button refresh = button("Download / refresh lists now");
        refresh.setOnClickListener(v -> {
            refresh.setEnabled(false);
            if (AdBlockVpnService.isRunning) {
                sendRefresh(true);
                toast("Refresh requested. Reopen settings to see last successful download.");
                refresh.setEnabled(true);
            } else {
                toast("Downloading enabled lists...");
                new Thread(() -> {
                    new BlocklistManager(getApplicationContext()).load(true);
                    runOnUiThread(() -> {
                        refresh.setEnabled(true);
                        refreshTime();
                        toast("List refresh finished. Stale cached lists are kept if offline.");
                    });
                }, "ManualFilterDownload").start();
            }
        });
        root.addView(refresh);
        label("Sources are downloaded over HTTPS. The previous cached copy is kept if an update fails.", 12, R.color.muted);

        domainEditor("Allowed domains (override all lists)", true);
        domainEditor("Blocked domains (also match subdomains)", false);
        installedApps();
    }

    @Override protected void onResume() {
        super.onResume();
        if (updateStatus != null) refreshTime();
    }

    private void refreshTime() {
        long timestamp = settings.lastDownloadedAt();
        updateStatus.setText(timestamp <= 0 ? "No successful source download recorded yet"
                : "Last successful download: "
                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new java.util.Date(timestamp)));
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

        Button add = button("Add domain");
        area.addView(add);
        LinearLayout entries = new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        area.addView(entries);
        Runnable redraw = () -> showDomains(entries, allow);
        add.setOnClickListener(v -> {
            if (!settings.addDomain(allow, input.getText().toString())) {
                input.setError("Enter a domain only (e.g. example.com)");
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
            TextView empty = small("No custom domains");
            entries.addView(empty);
            return;
        }
        for (String domain : domains) {
            Button remove = button(domain + "  —  Remove");
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
        section("Apps excluded from DNS filtering");
        label("Excluded apps bypass this VPN. Stop and start protection after changing these options.", 12, R.color.muted);
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
        if (installed.isEmpty()) label("No launchable applications found.", 12, R.color.muted);
        for (Map.Entry<String, String> entry : installed.entrySet()) {
            String pkg = entry.getKey();
            CheckBox checkbox = new CheckBox(this);
            checkbox.setText(entry.getValue() + " (" + pkg + ")");
            checkbox.setTextColor(getColor(R.color.text));
            checkbox.setTextSize(12);
            checkbox.setChecked(excluded.contains(pkg));
            checkbox.setOnCheckedChangeListener((b, selected) -> {
                settings.setExcluded(pkg, selected);
                toast("Restart VPN for app exclusions to apply.");
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
        b.setTextAllCaps(false);
        return b;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
