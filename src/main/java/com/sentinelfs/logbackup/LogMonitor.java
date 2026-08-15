package com.sentinelfs.logbackup;

import com.sentinelfs.core.FileRecord;
import com.sentinelfs.baseline.BaselineStore;
import com.sentinelfs.integrity.IntegrityChecker;
import com.sentinelfs.integrity.ScanFiles;

import java.io.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

public class LogMonitor {
    private static final Path LOG_BASELINE = Path.of("baseline_logs.json");
    private static final Path BACKUP_BASELINE = Path.of("baseline_backup.json");

    public static IntegrityChecker.ComparisonResult checkLogs(Path logDir) throws IOException, NoSuchAlgorithmException {
        return checkAgainstBaseline(logDir, LOG_BASELINE, "log");
    }
    public static IntegrityChecker.ComparisonResult checkBackups(Path backupDir) throws  IOException, NoSuchAlgorithmException {
        return checkAgainstBaseline(backupDir, BACKUP_BASELINE, "backup");
    }

    private static IntegrityChecker.ComparisonResult checkAgainstBaseline(Path targetDir, Path baselineFile, String label) throws IOException, NoSuchAlgorithmException {
        List<FileRecord> current = ScanFiles.scanDir(targetDir);

        if(!Files.exists(baselineFile)) {
            System.out.println("No " + label + " baseline found - creating one now");
            BaselineStore.save(current, baselineFile);
            System.out.println(label + " baseline saved with " + current.size() + "files.");
            return null;
        }

        List<FileRecord> baseline = BaselineStore.load(baselineFile);
        IntegrityChecker.ComparisonResult result = IntegrityChecker.compare(baseline, current);

        System.out.println(label + " check - Modified: " + result.modified.size() + " | Missing: " + result.deleted.size() + " | New: " + result.added.size() + " | OK: " + result.unchanged.size());

        if(label.equals("backup") && !result.deleted.isEmpty()) {
            System.out.println("WARNING: " + result.deleted.size() + " backup files missing or corrupted!");
        }
        return result;
    }

    public static void approveLogs(Path logDir) throws IOException, NoSuchAlgorithmException {
        List<FileRecord> current = ScanFiles.scanDir(logDir);
        BaselineStore.save(current, LOG_BASELINE);
        System.out.println("Log baseline updated with " + current.size() + " files.");
    }

    public static void approveBackups(Path backupDir) throws IOException, NoSuchAlgorithmException {
        List<FileRecord> current = ScanFiles.scanDir(backupDir);
        BaselineStore.save(current, BACKUP_BASELINE);
        System.out.println("Backup baseline updated with " + current.size() + " files");
    }
}