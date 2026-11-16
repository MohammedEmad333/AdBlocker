package com.example.adblocker;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.BooleanSupplier;

/** Same-directory, unique staging and safe replacement of validated cache files. */
final class BlocklistCacheFiles {
    private BlocklistCacheFiles() {}

    static File newStagingFile(File destination) throws IOException {
        if (destination == null) throw new IOException("Missing cache destination");
        File directory = destination.getAbsoluteFile().getParentFile();
        if (directory == null || !directory.isDirectory())
            throw new IOException("Cache directory unavailable");
        return File.createTempFile("blocklist-stage-", ".tmp", directory);
    }

    static void commit(File staged, File destination) throws IOException {
        commit(staged, destination, null);
    }

    static void commit(File staged, File destination,
                       BooleanSupplier stillAllowed) throws IOException {
        if (staged == null || destination == null || !staged.isFile()
                || staged.length() == 0)
            throw new IOException("Missing or empty staged cache");
        File stagingDir = staged.getCanonicalFile().getParentFile();
        File cacheDir = destination.getCanonicalFile().getParentFile();
        if (stagingDir == null || !stagingDir.equals(cacheDir))
            throw new IOException("Staged file is outside the cache directory");
        // Final boundary: cancelling a job must retain the previous good cache.
        BlocklistRefreshGuard.requireActive(stillAllowed);
        try {
            Files.move(staged.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException noAtomicMove) {
            BlocklistRefreshGuard.requireActive(stillAllowed);
            Files.move(staged.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
