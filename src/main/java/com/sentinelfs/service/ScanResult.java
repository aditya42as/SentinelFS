package com.sentinelfs.service;

import com.sentinelfs.config.WatchedFolder;
import com.sentinelfs.core.FileRecord;
import com.sentinelfs.integrity.IntegrityChecker.ComparisonResult;

import java.util.List;

public class ScanResult {
    public final WatchedFolder folder;
    public final ComparisonResult comparison;     // null when a fresh baseline was just created
    public final List<FileRecord> current;
    public final boolean baselineCreated;
    public final long elapsedMillis;

    public ScanResult(WatchedFolder folder, ComparisonResult comparison, List<FileRecord> current,
                      boolean baselineCreated, long elapsedMillis) {
        this.folder = folder;
        this.comparison = comparison;
        this.current = current;
        this.baselineCreated = baselineCreated;
        this.elapsedMillis = elapsedMillis;
    }

    public int changeCount() { return comparison == null ? 0 : comparison.changeCount(); }
}
