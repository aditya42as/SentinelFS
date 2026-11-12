package com.sentinelfs.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sentinelfs.alert.AlertEvent;
import com.sentinelfs.alert.ChangeType;
import com.sentinelfs.alert.Severity;
import com.sentinelfs.config.FolderKind;
import com.sentinelfs.core.FileRecord;

class IntegrityCheckerTest {

    private static FileRecord rec(String path, String hash) { return new FileRecord(path, hash, 1, 1); }

    @Test
    void classifiesModifiedAddedDeletedAndUnchanged() {
        List<FileRecord> baseline = List.of(rec("/a", "1"), rec("/b", "2"), rec("/c", "3"));
        List<FileRecord> current  = List.of(rec("/a", "1"), rec("/b", "CHANGED"), rec("/d", "4"));

        IntegrityChecker.ComparisonResult r = IntegrityChecker.compare(baseline, current);

        assertEquals(1, r.unchanged.size());
        assertEquals("/b", r.modified.get(0).getPath());
        assertEquals("/d", r.added.get(0).getPath());
        assertEquals("/c", r.deleted.get(0).getPath());
        assertEquals(3, r.changeCount());
    }

    @Test
    void severityDependsOnFolderKind() {
        assertEquals(Severity.WARNING,  AlertEvent.severityFor(ChangeType.MODIFIED, FolderKind.GENERAL));
        assertEquals(Severity.CRITICAL, AlertEvent.severityFor(ChangeType.DELETED, FolderKind.BACKUP));
        assertEquals(Severity.CRITICAL, AlertEvent.severityFor(ChangeType.MODIFIED, FolderKind.LOG));
        assertEquals(Severity.INFO,     AlertEvent.severityFor(ChangeType.ADDED, FolderKind.GENERAL));
        assertEquals(Severity.CRITICAL, AlertEvent.severityFor(ChangeType.BASELINE_TAMPERED, FolderKind.GENERAL));
    }
}
