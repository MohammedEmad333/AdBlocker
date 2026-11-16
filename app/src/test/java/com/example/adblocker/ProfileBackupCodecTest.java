package com.example.adblocker;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class ProfileBackupCodecTest {
    private static ProfileBackupCodec.Profile sample() throws IOException {
        Set<String> allow = new HashSet<>(Arrays.asList("z.example", "a.example"));
        Set<String> block = Collections.singleton("ads.example");
        Map<String, Boolean> urls = new LinkedHashMap<>();
        urls.put("https://lists.example.org/hosts", true);
        urls.put("https://other.example.org/hosts", false);
        return ProfileBackupCodec.create(allow, block,
                Collections.singleton("org.example.browser"), urls,
                true, false, true, DohProvider.QUAD9, false,
                true, AutoRefreshPolicy.WEEKLY, true);
    }

    private static ProfileBackupCodec.Profile parse(byte[] bytes) throws IOException {
        return ProfileBackupCodec.importProfile(new ByteArrayInputStream(bytes));
    }
    private static ProfileBackupCodec.Profile parse(String value) throws IOException {
        return parse(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void exportsDeterministicallyAndRoundTripsAllSettings() throws Exception {
        byte[] bytes = ProfileBackupCodec.exportProfile(sample());
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("ADBLOCKER-PROFILE-V1\n"));
        assertTrue(text.indexOf("ALLOW\ta.example\n") <
                text.indexOf("ALLOW\tz.example\n"));
        ProfileBackupCodec.Profile restored = parse(bytes);
        assertTrue(restored.allowed.contains("a.example"));
        assertTrue(restored.blocked.contains("ads.example"));
        assertEquals(Collections.singleton("org.example.browser"), restored.excludedPackages);
        assertEquals(2, restored.customSources.size());
        assertEquals(Boolean.TRUE, restored.customSources.get("https://lists.example.org/hosts"));
        assertEquals(Boolean.FALSE, restored.customSources.get("https://other.example.org/hosts"));
        assertFalse(restored.adAway);
        assertTrue(restored.stevenBlack);
        assertTrue(restored.dohEnabled);
        assertFalse(restored.dnsHistoryEnabled);
        assertTrue(restored.autoRefreshEnabled);
        assertTrue(restored.autoRefreshUnmeteredOnly);
        assertEquals(DohProvider.QUAD9, restored.dohProvider);
        assertEquals(AutoRefreshPolicy.WEEKLY, restored.autoRefreshFrequency);
        assertArrayEquals(bytes, ProfileBackupCodec.exportProfile(restored));
    }

    @Test public void rejectsUnknownFlagsDuplicatesAndUntrustedSources() throws Exception {
        String valid = new String(ProfileBackupCodec.exportProfile(sample()),
                StandardCharsets.UTF_8);
        for (String broken : Arrays.asList(
                valid.replace("STEVENBLACK\t1\n", ""),
                valid.replace("STEVENBLACK\t1\n", "STEVENBLACK\tmaybe\n"),
                valid.replace("DOH_PROVIDER\tquad9", "DOH_PROVIDER\tevil"),
                valid.replace("AUTO_FREQUENCY\tweekly", "AUTO_FREQUENCY\thourly"),
                valid.replace("ADAWAY\t0", "ADAWAY\t0\nADAWAY\t1"),
                valid.replace("CUSTOM\t1\thttps://lists.example.org/hosts",
                        "CUSTOM\t1\thttp://internal.example/hosts"),
                valid + "BLOCK\ta.example\n",
                valid + "EXCLUDE\torg.example.browser\n",
                valid + "CUSTOM\t1\thttps://lists.example.org/hosts\n",
                valid + "UNKNOWN\t1\n",
                valid + "ALLOW\tA.EXAMPLE\n"
        )) {
            try {
                parse(broken);
                fail("Accepted invalid profile: " + broken);
            } catch (IOException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    @Test public void rejectsOversizeInvalidEncodingAndMoreThanThreeUrls() throws Exception {
        byte[] tooLarge = new byte[ProfileBackupCodec.MAX_BYTES + 1];
        Arrays.fill(tooLarge, (byte) 'x');
        try {
            parse(tooLarge);
            fail("Oversized profile accepted");
        } catch (IOException expected) { /* reject */ }
        try {
            parse(new byte[]{(byte) 0xc3, (byte) 0x28});
            fail("Bad UTF-8 accepted");
        } catch (IOException expected) { /* reject */ }
        String valid = new String(ProfileBackupCodec.exportProfile(sample()),
                StandardCharsets.UTF_8);
        String tooMany = valid + "CUSTOM\t1\thttps://third.example.org/hosts\n"
                + "CUSTOM\t1\thttps://fourth.example.org/hosts\n";
        try {
            parse(tooMany);
            fail("Accepted more than three custom sources");
        } catch (IOException expected) { /* reject */ }
    }

    @Test public void doesNotIncludeDnsEventsOrRemoteCaches() throws Exception {
        String backup = new String(ProfileBackupCodec.exportProfile(sample()),
                StandardCharsets.UTF_8);
        assertFalse(backup.contains("DNS_EVENT"));
        assertFalse(backup.contains("cache_path"));
        assertFalse(backup.contains("last_successful_download"));
        assertFalse(backup.contains("api_token"));
    }

    @Test public void importedProfileIsImmutable() throws Exception {
        ProfileBackupCodec.Profile p = parse(ProfileBackupCodec.exportProfile(sample()));
        try {
            p.allowed.add("extra.example");
            fail("Mutable rule set");
        } catch (UnsupportedOperationException expected) {}
        try {
            p.customSources.put("https://new.example/hosts", true);
            fail("Mutable list catalog");
        } catch (UnsupportedOperationException expected) {}
    }
}
