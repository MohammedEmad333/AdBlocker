package com.example.adblocker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Portable UTF-8 backup for user-defined domains only, never DNS history. */
public final class RuleBackupCodec {
    static final String HEADER = "ADBLOCKER-RULES-V1";
    static final int MAX_BYTES = 128 * 1024;
    static final int MAX_RULES = 2000;

    private RuleBackupCodec() {}

    public static final class Rules {
        public final Set<String> allowed;
        public final Set<String> blocked;
        Rules(Set<String> allowed, Set<String> blocked) {
            this.allowed = Collections.unmodifiableSet(new HashSet<>(allowed));
            this.blocked = Collections.unmodifiableSet(new HashSet<>(blocked));
        }
    }

    public static byte[] exportRules(Set<String> allowed, Set<String> blocked)
            throws IOException {
        Rules rules = checked(allowed, blocked);
        List<String> a = new ArrayList<>(rules.allowed);
        List<String> b = new ArrayList<>(rules.blocked);
        Collections.sort(a);
        Collections.sort(b);
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (String domain : a) out.append("ALLOW\t").append(domain).append('\n');
        for (String domain : b) out.append("BLOCK\t").append(domain).append('\n');
        byte[] result = out.toString().getBytes(StandardCharsets.UTF_8);
        if (result.length > MAX_BYTES) throw new IOException("Rules backup too large");
        return result;
    }

    /** Reject the entire input rather than partially importing invalid rules. */
    public static Rules importRules(InputStream input) throws IOException {
        if (input == null) throw new IOException("No file");
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (data.size() + n > MAX_BYTES) throw new IOException("Rules backup too large");
            data.write(buffer, 0, n);
        }
        String[] lines = new String(data.toByteArray(), StandardCharsets.UTF_8)
                .split("\n", -1);
        if (lines.length == 0 || !HEADER.equals(stripCr(lines[0])))
            throw new IOException("Unsupported rules format");
        Set<String> allowed = new HashSet<>();
        Set<String> blocked = new HashSet<>();
        int count = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = stripCr(lines[i]);
            if (line.isEmpty()) continue;
            String[] columns = line.split("\t", -1);
            if (columns.length != 2) throw new IOException("Invalid rule on line " + (i + 1));
            String domain = DomainRules.normalize(columns[1]);
            // Reject noncanonical spelling and junk rather than silently changing rules.
            if (domain == null || !domain.equals(columns[1]))
                throw new IOException("Invalid domain on line " + (i + 1));
            if ("ALLOW".equals(columns[0])) allowed.add(domain);
            else if ("BLOCK".equals(columns[0])) blocked.add(domain);
            else throw new IOException("Unknown rule on line " + (i + 1));
            if (++count > MAX_RULES) throw new IOException("Too many rules");
        }
        return checked(allowed, blocked);
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static Rules checked(Set<String> allowed, Set<String> blocked)
            throws IOException {
        if (allowed == null || blocked == null
                || (long) allowed.size() + blocked.size() > MAX_RULES)
            throw new IOException("Too many rules");
        for (String name : allowed)
            if (name == null || !name.equals(DomainRules.normalize(name)))
                throw new IOException("Invalid allow domain");
        for (String name : blocked)
            if (name == null || !name.equals(DomainRules.normalize(name)))
                throw new IOException("Invalid block domain");
        for (String name : allowed)
            if (blocked.contains(name)) throw new IOException("Conflicting domain rules");
        return new Rules(allowed, blocked);
    }
}
