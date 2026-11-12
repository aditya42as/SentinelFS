package com.sentinelfs.watch;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.sentinelfs.alert.AlertEvent;
import com.sentinelfs.alert.ChangeType;
import com.sentinelfs.config.FolderKind;
import com.sentinelfs.core.FileRecord;
import com.sentinelfs.integrity.IgnoreMatcher;
import com.sentinelfs.integrity.ScanFiles;

/**
 * Real-time, recursive folder watcher that runs until {@link #close()} is called.
 *
 * Events from the OS are only a hint: each touched file is re-hashed and compared with the
 * trusted baseline, so a file that is edited and then restored produces no alert, and bursts of
 * duplicate events (very common on Windows) are debounced into one check.
 */
public class FileWatcher implements AutoCloseable {
    private static final long DEBOUNCE_MS = 400;
    private static final int MAX_ATTEMPTS = 6;

    private final Path root;
    private final String folderLabel;
    private final FolderKind kind;
    private final IgnoreMatcher ignore;
    private final Consumer<AlertEvent> sink;
    private final Runnable onOverflow;

    private volatile Map<String, FileRecord> baseline;
    private final Map<String, String> alertedState = new HashMap<>(); // path -> "TYPE:hash"
    private final Map<Path, Pending> pending = new LinkedHashMap<>();

    private WatchService watchService;
    private Thread thread;
    private volatile boolean running;

    private static final class Pending {
        long lastSeen = System.currentTimeMillis();
        int attempts;
    }

    public FileWatcher(Path root, String folderLabel, FolderKind kind, Map<String, FileRecord> baseline,
                       IgnoreMatcher ignore, Consumer<AlertEvent> sink, Runnable onOverflow) {
        this.root = root.toAbsolutePath().normalize();
        this.folderLabel = folderLabel;
        this.kind = kind;
        this.baseline = baseline;
        this.ignore = ignore;
        this.sink = sink;
        this.onOverflow = onOverflow;
    }

    public void start() throws IOException {
        if (running) return;
        watchService = FileSystems.getDefault().newWatchService();
        registerAll(root);
        running = true;
        thread = new Thread(this::loop, "sentinel-watch-" + root.getFileName());
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isRunning() { return running; }

    /** Swap in a new trusted baseline (after the user approves the current state). */
    public synchronized void updateBaseline(Map<String, FileRecord> newBaseline) {
        this.baseline = newBaseline;
        alertedState.clear();
    }

    @Override
    public void close() {
        running = false;
        try { if (watchService != null) watchService.close(); } catch (IOException ignored) { }
        if (thread != null) thread.interrupt();
    }

    // ---------------------------------------------------------------- internals

    private void loop() {
        try {
            while (running) {
                WatchKey key = watchService.poll(150, TimeUnit.MILLISECONDS);
                if (key != null) {
                    Path dir = (Path) key.watchable();
                    for (WatchEvent<?> ev : key.pollEvents()) {
                        if (ev.kind() == StandardWatchEventKinds.OVERFLOW) {
                            if (onOverflow != null) onOverflow.run();
                            continue;
                        }
                        Path changed = dir.resolve((Path) ev.context());
                        synchronized (pending) {
                            pending.computeIfAbsent(changed, p -> new Pending()).lastSeen = System.currentTimeMillis();
                        }
                    }
                    key.reset();
                }
                drainPending();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException e) {
            // normal shutdown
        } finally {
            running = false;
        }
    }

    private void drainPending() {
        long now = System.currentTimeMillis();
        java.util.List<Path> ready = new java.util.ArrayList<>();
        synchronized (pending) {
            for (Iterator<Map.Entry<Path, Pending>> it = pending.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Path, Pending> e = it.next();
                if (now - e.getValue().lastSeen >= DEBOUNCE_MS) {
                    ready.add(e.getKey());
                    it.remove();
                }
            }
        }
        for (Path p : ready) {
            if (!running) return;
            process(p, 1);
        }
    }

    private void retryLater(Path p, int attempt) {
        if (attempt >= MAX_ATTEMPTS) return;
        synchronized (pending) {
            Pending pd = pending.computeIfAbsent(p, x -> new Pending());
            pd.attempts = attempt;
            pd.lastSeen = System.currentTimeMillis() + 600; // wait a little longer (file probably locked)
        }
    }

    private synchronized void process(Path path, int attempt) {
        try {
            if (ignore.isIgnored(root, path)) return;
            String key = ScanFiles.normalize(path);

            if (!Files.exists(path)) {
                handleMissing(path, key);
                return;
            }
            if (Files.isDirectory(path)) {
                registerAll(path);
                try (Stream<Path> s = Files.walk(path)) {      // files that landed before we registered
                    s.filter(Files::isRegularFile).forEach(f -> {
                        synchronized (pending) { pending.computeIfAbsent(f, x -> new Pending()); }
                    });
                }
                return;
            }
            if (!Files.isRegularFile(path)) return;

            FileRecord now = ScanFiles.buildRecord(path);
            FileRecord base = baseline.get(key);
            if (base == null) {
                emit(ChangeType.ADDED, now);
            } else if (!base.getHash().equals(now.getHash())) {
                emit(ChangeType.MODIFIED, now);
            } else {
                alertedState.remove(key);   // back to the trusted state
            }
        } catch (IOException e) {
            retryLater(path, attempt + 1);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Hash algorithm missing: " + e.getMessage());
        }
    }

    private void handleMissing(Path path, String key) {
        FileRecord base = baseline.get(key);
        if (base != null) {
            emit(ChangeType.DELETED, base);
            return;
        }
        alertedState.remove(key);
        // a whole directory may have been removed/moved away
        String prefix = key + java.io.File.separator;
        for (FileRecord r : baseline.values()) {
            if (r.getPath().startsWith(prefix) && !Files.exists(Path.of(r.getPath()))) {
                emit(ChangeType.DELETED, r);
            }
        }
    }

    private void emit(ChangeType type, FileRecord record) {
        String state = type + ":" + record.getHash();
        if (state.equals(alertedState.get(record.getPath()))) return;   // already reported this exact state
        alertedState.put(record.getPath(), state);
        sink.accept(AlertEvent.of(type, record, folderLabel, kind));
    }

    private void registerAll(Path start) throws IOException {
        if (!Files.isDirectory(start)) return;
        try (Stream<Path> walk = Files.walk(start)) {
            walk.filter(Files::isDirectory).filter(d -> !ignore.isIgnored(root, d)).forEach(dir -> {
                try {
                    dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                } catch (IOException | ClosedWatchServiceException e) {
                    System.err.println("Could not watch: " + dir);
                }
            });
        }
    }
}
