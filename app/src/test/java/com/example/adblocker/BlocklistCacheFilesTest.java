package com.example.adblocker;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class BlocklistCacheFilesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static void write(File f, String value) throws Exception {
        Files.write(f.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }
    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void concurrentRefreshesUseDistinctStagingFiles() throws Exception {
        File cache = temp.newFile("blocklist.txt");
        write(cache, "known-good");
        File first = BlocklistCacheFiles.newStagingFile(cache);
        File second = BlocklistCacheFiles.newStagingFile(cache);
        try {
            assertNotEquals(first.getCanonicalPath(), second.getCanonicalPath());
            assertEquals(cache.getParentFile(), first.getParentFile());
            assertEquals(cache.getParentFile(), second.getParentFile());
            write(first, "new-list-A");
            write(second, "new-list-B");
            assertEquals("known-good", read(cache));
            BlocklistCacheFiles.commit(first, cache);
            assertEquals("new-list-A", read(cache));
            BlocklistCacheFiles.commit(second, cache);
            assertEquals("new-list-B", read(cache));
        } finally {
            first.delete();
            second.delete();
        }
    }

    @Test public void emptyDownloadMustNotReplaceExistingCache() throws Exception {
        File cache = temp.newFile("blocklist.txt");
        write(cache, "known-good");
        File emptyStage = BlocklistCacheFiles.newStagingFile(cache);
        try {
            BlocklistCacheFiles.commit(emptyStage, cache);
            fail("An incomplete download must not be committed");
        } catch (IOException expected) {
            assertEquals("known-good", read(cache));
        } finally {
            emptyStage.delete();
        }
    }

    @Test public void wrongDirectoryCannotReplaceCache() throws Exception {
        File cache = temp.newFile("blocklist.txt");
        write(cache, "known-good");
        File other = new File(temp.newFolder("untrusted"), "other.tmp");
        write(other, "replacement");
        try {
            BlocklistCacheFiles.commit(other, cache);
            fail("Staging outside the cache directory must be rejected");
        } catch (IOException expected) {
            assertEquals("known-good", read(cache));
        }
    }

    @Test public void inaccessibleCacheDirectoryIsRejected() throws Exception {
        File missing = new File(temp.getRoot(), "missing/hosts");
        try {
            BlocklistCacheFiles.newStagingFile(missing);
            fail("Missing directory must fail cleanly");
        } catch (IOException expected) {
            assertFalse(missing.exists());
        }
    }
}
