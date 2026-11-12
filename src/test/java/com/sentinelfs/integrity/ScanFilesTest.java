package com.sentinelfs.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sentinelfs.core.FileRecord;

class ScanFilesTest {

    @Test
    void scansRecursivelyAndSkipsIgnoredFolders(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("src/deep"));
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve("a.txt"), "a");
        Files.writeString(dir.resolve("src/deep/b.txt"), "b");
        Files.writeString(dir.resolve(".git/config"), "secret");
        Files.writeString(dir.resolve("junk.tmp"), "x");

        List<FileRecord> recs = ScanFiles.scanDir(dir, new IgnoreMatcher(List.of(".git", "*.tmp")), null, null);

        assertEquals(2, recs.size());
        assertTrue(recs.stream().anyMatch(r -> r.getPath().endsWith("b.txt")));
    }

    @Test
    void cancelFlagAbortsTheScan(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.txt"), "a");
        AtomicBoolean cancel = new AtomicBoolean(true);
        assertThrows(ScanFiles.ScanCancelledException.class,
                () -> ScanFiles.scanDir(dir, IgnoreMatcher.NONE, null, cancel));
    }
}
