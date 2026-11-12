package com.sentinelfs.service;

import com.sentinelfs.alert.AlertEvent;
import com.sentinelfs.alert.AlertHistory;
import com.sentinelfs.alert.AlertManager;
import com.sentinelfs.alert.ChangeType;
import com.sentinelfs.alert.FileLogListener;
import com.sentinelfs.alert.Severity;
import com.sentinelfs.baseline.BaselineStore;
import com.sentinelfs.baseline.BaselineTamperedException;
import com.sentinelfs.config.AppConfig;
import com.sentinelfs.config.AppPaths;
import com.sentinelfs.config.ConfigStore;
import com.sentinelfs.config.FolderKind;
import com.sentinelfs.config.WatchedFolder;
import com.sentinelfs.core.FileRecord;
import com.sentinelfs.integrity.IgnoreMatcher;
import com.sentinelfs.integrity.IntegrityChecker;
import com.sentinelfs.integrity.IntegrityChecker.ComparisonResult;
import com.sentinelfs.integrity.ScanFiles;
import com.sentinelfs.watch.FileWatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/**
 * The application "engine". Both the GUI and the console front-end drive this class; it knows
 * nothing about Swing.
 */
public class MonitorService implements AutoCloseable {
    private final AppConfig config;
    private final AlertManager alerts = new AlertManager();
    private final AlertHistory history;

    private final Map<String, FileWatcher> watchers = new HashMap<>();
    private final Set<String> reported = new HashSet<>();            // path|type|hash already alerted
    private final List<Runnable> stateListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sentinel-scheduler");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> scheduled;
    private volatile boolean monitoring;

    public MonitorService() {
        this.config = ConfigStore.load();
        this.history = new AlertHistory(AppPaths.historyFile());
        alerts.addListener(new FileLogListener(AppPaths.alertsLog()));
        alerts.addListener(history);
        for (WatchedFolder f : config.folders) refreshStatus(f);
    }

    // ------------------------------------------------------------ accessors
    public AlertManager alerts()     { return alerts; }
    public AlertHistory history()    { return history; }
    public AppConfig config()        { return config; }
    public List<WatchedFolder> folders() { return config.folders; }
    public boolean isMonitoring()    { return monitoring; }

    public void addStateListener(Runnable r) { stateListeners.add(r); }
    private void changed() { for (Runnable r : stateListeners) { try { r.run(); } catch (RuntimeException ignored) { } } }

    public void saveConfig() throws IOException {
        ConfigStore.save(config);
        if (monitoring) reschedule();
        changed();
    }

    public IgnoreMatcher ignoreMatcher() { return new IgnoreMatcher(config.ignorePatterns); }

    public Path baselinePath(WatchedFolder f) {
        return AppPaths.baselinesDir().resolve(f.baselineId() + ".json");
    }
    public boolean hasBaseline(WatchedFolder f) { return Files.exists(baselinePath(f)); }

    // ------------------------------------------------------------ folder management
    public synchronized WatchedFolder addFolder(Path dir, FolderKind kind) throws IOException {
        if (!Files.isDirectory(dir)) throw new IOException("Not a folder: " + dir);
        WatchedFolder f = new WatchedFolder(dir, kind);
        for (WatchedFolder existing : config.folders) {
            if (existing.pathString().equalsIgnoreCase(f.pathString())) throw new IOException("That folder is already protected.");
        }
        config.folders.add(f);
        ConfigStore.save(config);
        refreshStatus(f);
        changed();
        return f;
    }

    public synchronized void removeFolder(WatchedFolder f) throws IOException {
        stopWatcher(f);
        config.folders.remove(f);
        BaselineStore.delete(baselinePath(f));
        ConfigStore.save(config);
        changed();
    }

    // ------------------------------------------------------------ scanning
    /** Hash the folder and save it as the trusted baseline. */
    public List<FileRecord> createBaseline(WatchedFolder f, IntConsumer progress, AtomicBoolean cancel) throws IOException {
        List<FileRecord> current = createBaselineInternal(f, progress, cancel);
        synchronized (this) {
            if (monitoring && !watchers.containsKey(f.pathString())) startWatcher(f);
        }
        return current;
    }

    private List<FileRecord> createBaselineInternal(WatchedFolder f, IntConsumer progress, AtomicBoolean cancel) throws IOException {
        List<FileRecord> current = ScanFiles.scanDir(f.path(), ignoreMatcher(), progress, cancel);
        BaselineStore.save(current, baselinePath(f));
        f.recordScan(current.size());
        f.setStatus("Protected", 0);
        synchronized (this) {
            reported.removeIf(k -> k.startsWith(f.pathString() + "|") || isInside(k, f));
            FileWatcher w = watchers.get(f.pathString());
            if (w != null) w.updateBaseline(toMap(current));
        }
        persistQuietly();
        changed();
        return current;
    }

    /**
     * Compare the folder with its baseline. If there is no baseline yet one is created.
     * New (not yet reported) changes are sent to the alert listeners when {@code fireAlerts} is set.
     */
    public ScanResult scan(WatchedFolder f, boolean fireAlerts, IntConsumer progress, AtomicBoolean cancel) throws IOException {
        long t0 = System.currentTimeMillis();
        if (!Files.isDirectory(f.path())) {
            f.setStatus("Folder missing", 0);
            changed();
            throw new IOException("Folder no longer exists: " + f.pathString());
        }
        if (!hasBaseline(f)) {
            List<FileRecord> cur = createBaseline(f, progress, cancel);
            return new ScanResult(f, null, cur, true, System.currentTimeMillis() - t0);
        }
        List<FileRecord> baseline;
        try {
            baseline = BaselineStore.load(baselinePath(f));
        } catch (BaselineTamperedException e) {
            f.setStatus("Baseline TAMPERED", 0);
            alerts.fireAlert(new AlertEvent(System.currentTimeMillis(), ChangeType.BASELINE_TAMPERED, Severity.CRITICAL,
                    baselinePath(f).toString(), f.pathString(), ""));
            changed();
            throw e;
        }
        List<FileRecord> current = ScanFiles.scanDir(f.path(), ignoreMatcher(), progress, cancel);
        ComparisonResult cmp = IntegrityChecker.compare(baseline, current);
        f.recordScan(current.size());
        f.setStatus(cmp.hasChanges() ? cmp.changeCount() + " change(s)" : "Clean", cmp.changeCount());
        if (fireAlerts) {
            IntegrityChecker.reportAlerts(cmp, f.pathString(), f.kind(), ev -> {
                if (markReported(ev)) alerts.fireAlert(ev);
            });
        }
        persistQuietly();
        changed();
        return new ScanResult(f, cmp, current, false, System.currentTimeMillis() - t0);
    }

    // ------------------------------------------------------------ real-time monitoring
    public synchronized void startMonitoring() {
        if (monitoring) return;
        monitoring = true;
        for (WatchedFolder f : config.folders) startWatcher(f);
        reschedule();
        changed();
    }

    public synchronized void stopMonitoring() {
        if (!monitoring) return;
        monitoring = false;
        for (FileWatcher w : watchers.values()) w.close();
        watchers.clear();
        if (scheduled != null) { scheduled.cancel(false); scheduled = null; }
        changed();
    }

    private void startWatcher(WatchedFolder f) {
        if (watchers.containsKey(f.pathString())) return;
        if (!Files.isDirectory(f.path())) { f.setStatus("Folder missing", 0); return; }
        try {
            if (!hasBaseline(f)) {
                // nothing to compare against yet: first hash the folder, then watch it
                createBaselineInternal(f, null, null);
            }
            List<FileRecord> base = BaselineStore.load(baselinePath(f));
            FileWatcher w = new FileWatcher(f.path(), f.pathString(), f.kind(), toMap(base), ignoreMatcher(),
                    ev -> {
                        markReported(ev);
                        int n = f.pendingChanges() + 1;
                        f.setStatus(n + " change(s)", n);
                        alerts.fireAlert(ev);
                        changed();
                    },
                    () -> scheduler.execute(() -> quietScan(f)));
            w.start();
            watchers.put(f.pathString(), w);
            if (f.pendingChanges() == 0) f.setStatus("Protected", 0);
        } catch (BaselineTamperedException e) {
            f.setStatus("Baseline TAMPERED", 0);
            alerts.fireAlert(new AlertEvent(System.currentTimeMillis(), ChangeType.BASELINE_TAMPERED, Severity.CRITICAL,
                    baselinePath(f).toString(), f.pathString(), ""));
        } catch (IOException e) {
            f.setStatus("Error: " + e.getMessage(), 0);
        }
    }

    private void stopWatcher(WatchedFolder f) {
        FileWatcher w = watchers.remove(f.pathString());
        if (w != null) w.close();
    }

    private void reschedule() {
        if (scheduled != null) { scheduled.cancel(false); scheduled = null; }
        int minutes = config.scanIntervalMinutes;
        if (monitoring && minutes > 0) {
            scheduled = scheduler.scheduleWithFixedDelay(this::scanAllQuietly, minutes, minutes, TimeUnit.MINUTES);
        }
    }

    private void scanAllQuietly() {
        for (WatchedFolder f : new ArrayList<>(config.folders)) quietScan(f);
    }

    private void quietScan(WatchedFolder f) {
        try { scan(f, true, null, null); } catch (Exception e) { System.err.println("Scheduled scan failed for " + f + ": " + e.getMessage()); }
    }

    // ------------------------------------------------------------ helpers
    private void refreshStatus(WatchedFolder f) {
        if (!Files.isDirectory(f.path())) f.setStatus("Folder missing", 0);
        else if (!hasBaseline(f)) f.setStatus("No baseline", 0);
        else f.setStatus("Protected", 0);
    }

    private synchronized boolean markReported(AlertEvent ev) {
        return reported.add(ev.getPath() + "|" + ev.getType() + "|" + ev.getHash());
    }

    private static boolean isInside(String key, WatchedFolder f) {
        return key.startsWith(f.pathString() + java.io.File.separator);
    }

    private static Map<String, FileRecord> toMap(List<FileRecord> list) {
        Map<String, FileRecord> m = new HashMap<>();
        for (FileRecord r : list) m.put(r.getPath(), r);
        return m;
    }

    private void persistQuietly() {
        try { ConfigStore.save(config); } catch (IOException e) { System.err.println("Could not save config: " + e.getMessage()); }
    }

    @Override
    public void close() {
        stopMonitoring();
        scheduler.shutdownNow();
        persistQuietly();
    }
}
