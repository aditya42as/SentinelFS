package com.sentinelfs;

import java.nio.file.Path;
import java.util.*;

import com.sentinelfs.baseline.BaselineStore;
import com.sentinelfs.core.FileRecord;
import com.sentinelfs.integrity.ScanFiles;
import com.sentinelfs.integrity.IntegrityChecker;
import com.sentinelfs.integrity.ScanFiles;
import com.sentinelfs.logbackup.LogMonitor;

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
    }
}