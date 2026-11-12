package com.sentinelfs.integrity;

import com.sentinelfs.alert.AlertEvent;
import com.sentinelfs.alert.AlertManager;
import com.sentinelfs.alert.ChangeType;
import com.sentinelfs.config.FolderKind;
import com.sentinelfs.core.FileRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class IntegrityChecker {
    public static class ComparisonResult {
        public List<FileRecord> modified = new ArrayList<>();
        public List<FileRecord> added = new ArrayList<>();
        public List<FileRecord> deleted = new ArrayList<>();
        public List<FileRecord> unchanged = new ArrayList<>();

        public int changeCount() { return modified.size() + added.size() + deleted.size(); }
        public boolean hasChanges() { return changeCount() > 0; }
    }

    public static ComparisonResult compare(List<FileRecord> baseline, List<FileRecord> current) {
        ComparisonResult result = new ComparisonResult();

        Map<String, FileRecord> baselineByPath = new HashMap<>();
        for (FileRecord r : baseline) baselineByPath.put(r.getPath(), r);
        Map<String, FileRecord> currentByPath = new HashMap<>();
        for (FileRecord r : current) currentByPath.put(r.getPath(), r);

        for (FileRecord cur : current) {
            FileRecord base = baselineByPath.get(cur.getPath());
            if (base == null) {
                result.added.add(cur);
            } else if (!base.getHash().equals(cur.getHash())) {
                result.modified.add(cur);
            } else {
                result.unchanged.add(cur);
            }
        }
        for (FileRecord base : baseline) {
            if (!currentByPath.containsKey(base.getPath())) result.deleted.add(base);
        }
        return result;
    }

    /** Fires one alert per change through {@code sink}. */
    public static void reportAlerts(ComparisonResult result, String folder, FolderKind kind, Consumer<AlertEvent> sink) {
        for (FileRecord r : result.modified) sink.accept(AlertEvent.of(ChangeType.MODIFIED, r, folder, kind));
        for (FileRecord r : result.added)    sink.accept(AlertEvent.of(ChangeType.ADDED, r, folder, kind));
        for (FileRecord r : result.deleted)  sink.accept(AlertEvent.of(ChangeType.DELETED, r, folder, kind));
    }

    public static void reportAlerts(ComparisonResult result, AlertManager alertManager, String folder, FolderKind kind) {
        reportAlerts(result, folder, kind, alertManager::fireAlert);
    }
}
