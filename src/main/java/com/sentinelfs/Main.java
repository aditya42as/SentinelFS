package com.sentinelfs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.sentinelfs.alert.AlertManager;
import com.sentinelfs.alert.ConsoleAlertListener;
import com.sentinelfs.alert.FileLogListener;
import com.sentinelfs.baseline.BaselineStore;
import com.sentinelfs.core.FileRecord;
import com.sentinelfs.integrity.IntegrityChecker;
import com.sentinelfs.integrity.ScanFiles;
import com.sentinelfs.logbackup.LogMonitor;
import com.sentinelfs.watch.FileWatcher;

public class Main {

    public static void main(String[] args) throws Exception {
        Path baselineFile = Path.of("baseline.json");
        Path scanTarget = Path.of("src");

        if (!java.nio.file.Files.exists(baselineFile)) {
            System.out.println("No baseline found — creating one now.");
            List<FileRecord> records = ScanFiles.scanDir(scanTarget);
            BaselineStore.save(records, baselineFile);
            System.out.println("Baseline saved with " + records.size() + " files.");
            return;
        }

        System.out.println("Baseline found — running integrity check...");
        List<FileRecord> baseline = BaselineStore.load(baselineFile);
        List<FileRecord> current = ScanFiles.scanDir(scanTarget);

        IntegrityChecker.ComparisonResult result = IntegrityChecker.compare(baseline, current);

        System.out.println("Modified: " + result.modified.size());
        System.out.println("Added: " + result.added.size());
        System.out.println("Deleted: " + result.deleted.size());
        System.out.println("Unchanged: " + result.unchanged.size());
        LogMonitor.checkLogs(Path.of("src"));

        AlertManager alertManager = new AlertManager();
        alertManager.addListener(new ConsoleAlertListener());
        alertManager.addListener(new FileLogListener(Path.of("alerts.log")));

        IntegrityChecker.reportAlerts(result, alertManager);

        FileWatcher watcher = new FileWatcher(Path.of("src"), alertManager);
        Thread watcThread = new Thread(() -> {
            try {
                watcher.startWatching();
            } catch (IOException e) {
                System.out.println("Watcher failed: " + e.getMessage());
            }
        });
        watcThread.setDaemon(true);
        watcThread.start();

        System.err.println("Real time monitoring active. Try editing a file in src now");
        Thread.sleep(30000);
        
    }
}