package com.example.adblocker;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.*;

public class RuleBackupCodecTest {
    private RuleBackupCodec.Rules parse(String text) throws IOException {
        return RuleBackupCodec.importRules(new ByteArrayInputStream(
                text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void roundTripsInDeterministicOrder() throws Exception {
        byte[] data = RuleBackupCodec.exportRules(
                new HashSet<>(Arrays.asList("z.example", "a.example")),
                Collections.singleton("blocked.example"));
        String text = new String(data, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("ADBLOCKER-RULES-V1\nALLOW\ta.example\nALLOW\tz.example"));
        RuleBackupCodec.Rules result = parse(text);
        assertEquals(2, result.allowed.size());
        assertTrue(result.blocked.contains("blocked.example"));
        assertEquals(text, new String(RuleBackupCodec.exportRules(
                result.allowed, result.blocked), StandardCharsets.UTF_8));
    }

    @Test public void emptyBackupAndWindowsLineEndingsWork() throws Exception {
        assertTrue(parse("ADBLOCKER-RULES-V1\r\n").allowed.isEmpty());
        assertTrue(parse("ADBLOCKER-RULES-V1\r\nBLOCK\tads.example\r\n")
                .blocked.contains("ads.example"));
    }

    @Test public void invalidRuleRejectsEntireFile() {
        for (String text : Arrays.asList("WRONG\nALLOW\ta.example",
                "ADBLOCKER-RULES-V1\nBLOCK\thttps://bad.example",
                "ADBLOCKER-RULES-V1\nALLOW\ta.example\nBLOCK\ta.example",
                "ADBLOCKER-RULES-V1\nDELETE\ta.example",
                "ADBLOCKER-RULES-V1\nALLOW\tA.EXAMPLE")) {
            try {
                parse(text);
                fail("Accepted invalid input: " + text);
            } catch (IOException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    @Test public void rejectsOversizedInputBeforeParsing() {
        byte[] oversized = new byte[RuleBackupCodec.MAX_BYTES + 1];
        Arrays.fill(oversized, (byte) 'x');
        try {
            RuleBackupCodec.importRules(new ByteArrayInputStream(oversized));
            fail("Accepted oversized data");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
