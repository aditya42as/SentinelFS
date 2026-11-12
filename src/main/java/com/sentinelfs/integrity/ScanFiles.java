package com.sentinelfs.integrity;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import com.sentinelfs.core.FileRecord;
import com.sentinelfs.core.HashingUnit;

public class ScanFiles {

    /** Thrown when a running scan is cancelled via the cancel flag. */
    public static class ScanCancelledException extends RuntimeException {
        public ScanCancelledException() { super("Scan cancelled"); }
    }

    public static FileRecord buildRecord(Path filePath) throws IOException, NoSuchAlgorithmException {
        String hash = HashingUnit.hashFile(filePath);
        long size = Files.size(filePath);
        long lastModified = Files.getLastModifiedTime(filePath).toMillis();
        return new FileRecord(normalize(filePath), hash, size, lastModified);
    }

    public static String normalize(Path p) {
        return p.toAbsolutePath().normalize().toString();
    }

    public static List<FileRecord> scanDir(Path root) throws IOException {
        return scanDir(root, IgnoreMatcher.NONE, null, null);
    }

    /**
     * Hashes every regular file under {@code root}.
     * @param progress called with the number of files hashed so far (from worker threads)
     * @param cancel   set to true to abort; throws {@link ScanCancelledException}
     */
    public static List<FileRecord> scanDir(Path root, IgnoreMatcher ignore, IntConsumer progress, AtomicBoolean cancel) throws IOException {
        Path absRoot = root.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();

        Files.walkFileTree(absRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (cancel != null && cancel.get()) throw new ScanCancelledException();
                return ignore.isIgnored(absRoot, dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && !ignore.isIgnored(absRoot, file)) files.add(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("Skipping unreadable path: " + file + " (" + exc.getMessage() + ")");
                return FileVisitResult.CONTINUE;
            }
        });

        ConcurrentLinkedQueue<FileRecord> out = new ConcurrentLinkedQueue<>();
        AtomicInteger done = new AtomicInteger();
        files.parallelStream().forEach(f -> {
            if (cancel != null && cancel.get()) throw new ScanCancelledException();
            try {
                out.add(buildRecord(f));
            } catch (IOException | NoSuchAlgorithmException e) {
                System.err.println("Skipping unreadable file: " + f + " (" + e.getMessage() + ")");
            }
            int n = done.incrementAndGet();
            if (progress != null && (n % 25 == 0 || n == files.size())) progress.accept(n);
        });

        List<FileRecord> result = new ArrayList<>(out);
        result.sort(Comparator.comparing(FileRecord::getPath));
        return result;
    }
}
