package com.example.adblocker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Versioned, bounded, portable configuration backup.
 * No DNS history, cached remote lists, diagnostics, tokens or timestamps.
 * This is plain UTF-8, not encrypted; users should store backups privately.
 */
final class ProfileBackupCodec {
    static final String HEADER = "ADBLOCKER-PROFILE-V1";
    static final int MAX_BYTES = 256 * 1024;
    static final int MAX_EXCLUSIONS = 200;

    static final class Profile {
        final Set<String> allowed;
        final Set<String> blocked;
        final Set<String> excludedPackages;
        final Map<String, Boolean> customSources;
        final boolean stevenBlack, adAway, dohEnabled, dnsHistoryEnabled;
        final boolean autoRefreshEnabled, autoRefreshUnmeteredOnly;
        final String dohProvider, autoRefreshFrequency;

        private Profile(Set<String> allowed, Set<String> blocked,
                Set<String> excludedPackages, Map<String, Boolean> customSources,
                boolean stevenBlack, boolean adAway, boolean dohEnabled,
                String dohProvider, boolean dnsHistoryEnabled,
                boolean autoRefreshEnabled, String autoRefreshFrequency,
                boolean autoRefreshUnmeteredOnly) {
            this.allowed = Collections.unmodifiableSet(new HashSet<>(allowed));
            this.blocked = Collections.unmodifiableSet(new HashSet<>(blocked));
            this.excludedPackages = Collections.unmodifiableSet(new HashSet<>(excludedPackages));
            this.customSources = Collections.unmodifiableMap(new LinkedHashMap<>(customSources));
            this.stevenBlack = stevenBlack;
            this.adAway = adAway;
            this.dohEnabled = dohEnabled;
            this.dohProvider = dohProvider;
            this.dnsHistoryEnabled = dnsHistoryEnabled;
            this.autoRefreshEnabled = autoRefreshEnabled;
            this.autoRefreshFrequency = autoRefreshFrequency;
            this.autoRefreshUnmeteredOnly = autoRefreshUnmeteredOnly;
        }
    }

    private ProfileBackupCodec() {}

    static Profile create(Set<String> allowed, Set<String> blocked,
            Set<String> excludedPackages, Map<String, Boolean> customSources,
            boolean stevenBlack, boolean adAway, boolean dohEnabled,
            String dohProvider, boolean dnsHistoryEnabled,
            boolean autoRefreshEnabled, String autoRefreshFrequency,
            boolean autoRefreshUnmeteredOnly) throws IOException {
        // Reuse the existing canonical rule checks and the 2,000-rule limit.
        RuleBackupCodec.exportRules(allowed, blocked);
        if (excludedPackages == null || excludedPackages.size() > MAX_EXCLUSIONS
                || customSources == null || customSources.size() > CustomSourceCatalog.LIMIT)
            throw new IOException("Too many profile entries");
        for (String pkg : excludedPackages) {
            if (pkg == null || pkg.length() > 255 ||
                    !pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
                throw new IOException("Invalid excluded app package");
        }
        for (Map.Entry<String, Boolean> source : customSources.entrySet()) {
            String url = source.getKey();
            if (url == null || !url.equals(CustomBlocklistUrl.normalize(url))
                    || source.getValue() == null)
                throw new IOException("Invalid custom HTTPS source");
        }
        if (!DohProvider.CLOUDFLARE.equals(dohProvider)
                && !DohProvider.GOOGLE.equals(dohProvider)
                && !DohProvider.QUAD9.equals(dohProvider))
            throw new IOException("Unknown encrypted DNS provider");
        if (!AutoRefreshPolicy.DAILY.equals(autoRefreshFrequency)
                && !AutoRefreshPolicy.WEEKLY.equals(autoRefreshFrequency))
            throw new IOException("Invalid auto-refresh frequency");
        return new Profile(allowed, blocked, excludedPackages, customSources,
                stevenBlack, adAway, dohEnabled, dohProvider,
                dnsHistoryEnabled, autoRefreshEnabled, autoRefreshFrequency,
                autoRefreshUnmeteredOnly);
    }

    private static String flag(boolean value) { return value ? "1" : "0"; }

    static byte[] exportProfile(Profile profile) throws IOException {
        if (profile == null) throw new IOException("Missing profile");
        Profile checked = create(profile.allowed, profile.blocked, profile.excludedPackages,
                profile.customSources, profile.stevenBlack, profile.adAway,
                profile.dohEnabled, profile.dohProvider, profile.dnsHistoryEnabled,
                profile.autoRefreshEnabled, profile.autoRefreshFrequency,
                profile.autoRefreshUnmeteredOnly);
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        out.append("STEVENBLACK\t").append(flag(checked.stevenBlack)).append('\n');
        out.append("ADAWAY\t").append(flag(checked.adAway)).append('\n');
        out.append("DOH\t").append(flag(checked.dohEnabled)).append('\n');
        out.append("DOH_PROVIDER\t").append(checked.dohProvider).append('\n');
        out.append("HISTORY\t").append(flag(checked.dnsHistoryEnabled)).append('\n');
        out.append("AUTO\t").append(flag(checked.autoRefreshEnabled)).append('\n');
        out.append("AUTO_FREQUENCY\t").append(checked.autoRefreshFrequency).append('\n');
        out.append("AUTO_UNMETERED\t").append(flag(checked.autoRefreshUnmeteredOnly))
                .append('\n');
        appendSorted(out, "ALLOW", checked.allowed);
        appendSorted(out, "BLOCK", checked.blocked);
        appendSorted(out, "EXCLUDE", checked.excludedPackages);
        List<String> urls = new ArrayList<>(checked.customSources.keySet());
        Collections.sort(urls);
        for (String url : urls) {
            out.append("CUSTOM\t").append(flag(checked.customSources.get(url)))
                    .append('\t').append(url).append('\n');
        }
        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Profile backup too large");
        return bytes;
    }

    private static void appendSorted(StringBuilder out, String kind, Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        for (String value : sorted) {
            out.append(kind).append('\t').append(value).append('\n');
        }
    }

    /** Parse, validate every field, then hand back an immutable snapshot. */
    static Profile importProfile(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing backup file");
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (data.size() + n > MAX_BYTES) throw new IOException("Profile backup too large");
            data.write(buffer, 0, n);
        }
        final String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data.toByteArray())).toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Profile is not valid UTF-8", invalid);
        }

        String[] lines = text.split("\n", -1);
        if (lines.length < 9 || !HEADER.equals(stripCr(lines[0])))
            throw new IOException("Unsupported profile format");
        Set<String> allowed = new HashSet<>();
        Set<String> blocked = new HashSet<>();
        Set<String> excluded = new HashSet<>();
        Map<String, Boolean> custom = new LinkedHashMap<>();
        Map<String, String> prefs = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String row = stripCr(lines[i]);
            if (i == lines.length - 1 && row.isEmpty()) break;
            String[] fields = row.split("\t", -1);
            if (fields.length == 3 && "CUSTOM".equals(fields[0])) {
                String url = fields[2];
                if (!"0".equals(fields[1]) && !"1".equals(fields[1]))
                    throw new IOException("Invalid custom source flag");
                if (!url.equals(CustomBlocklistUrl.normalize(url))
                        || custom.putIfAbsent(url, "1".equals(fields[1])) != null
                        || custom.size() > CustomSourceCatalog.LIMIT)
                    throw new IOException("Invalid or duplicate custom source");
            } else if (fields.length == 2) {
                String kind = fields[0], value = fields[1];
                switch (kind) {
                    case "ALLOW":
                        if (!allowed.add(value) || allowed.size() + blocked.size()
                                > RuleBackupCodec.MAX_RULES)
                            throw new IOException("Invalid or duplicate allow rule");
                        break;
                    case "BLOCK":
                        if (!blocked.add(value) || allowed.size() + blocked.size()
                                > RuleBackupCodec.MAX_RULES)
                            throw new IOException("Invalid or duplicate block rule");
                        break;
                    case "EXCLUDE":
                        if (!excluded.add(value) || excluded.size() > MAX_EXCLUSIONS)
                            throw new IOException("Invalid or duplicate package");
                        break;
                    case "STEVENBLACK": case "ADAWAY": case "DOH":
                    case "DOH_PROVIDER": case "HISTORY": case "AUTO":
                    case "AUTO_FREQUENCY": case "AUTO_UNMETERED":
                        if (prefs.putIfAbsent(kind, value) != null)
                            throw new IOException("Duplicate profile setting");
                        break;
                    default: throw new IOException("Unknown profile entry");
                }
            } else {
                throw new IOException("Malformed profile entry");
            }
        }
        return create(allowed, blocked, excluded, custom,
                parseFlag(prefs, "STEVENBLACK"), parseFlag(prefs, "ADAWAY"),
                parseFlag(prefs, "DOH"), require(prefs, "DOH_PROVIDER"),
                parseFlag(prefs, "HISTORY"), parseFlag(prefs, "AUTO"),
                require(prefs, "AUTO_FREQUENCY"), parseFlag(prefs, "AUTO_UNMETERED"));
    }

    private static String require(Map<String, String> fields, String key)
            throws IOException {
        String value = fields.get(key);
        if (value == null) throw new IOException("Missing setting: " + key);
        return value;
    }

    private static boolean parseFlag(Map<String, String> fields, String key)
            throws IOException {
        String value = require(fields, key);
        if ("1".equals(value)) return true;
        if ("0".equals(value)) return false;
        throw new IOException("Invalid setting: " + key);
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
