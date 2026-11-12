package com.sentinelfs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sentinelfs.alert.AlertEvent;
import com.sentinelfs.alert.ChangeType;
import com.sentinelfs.alert.Severity;
import com.sentinelfs.baseline.BaselineTamperedException;
import com.sentinelfs.config.FolderKind;
import com.sentinelfs.config.WatchedFolder;

class MonitorServiceTest {

    private MonitorService newService(Path home) {
        System.setProperty("sentinelfs.home", home.toString());
        return new MonitorService();
    }

    @Test
    void firstScanCreatesBaselineThenDetectsChangesAndApprovalClearsThem(@TempDir Path tmp) throws Exception {
        Path watched = Files.createDirectory(tmp.resolve("watched"));
        Files.writeString(watched.resolve("a.txt"), "one");
        try (MonitorService svc = newService(tmp.resolve("home"))) {
            WatchedFolder f = svc.addFolder(watched, FolderKind.GENERAL);

            assertTrue(svc.scan(f, false, null, null).baselineCreated);
            assertFalse(svc.scan(f, false, null, null).comparison.hasChanges());

            Files.writeString(watched.resolve("a.txt"), "two");
            Files.writeString(watched.resolve("b.txt"), "new");
            ScanResult r = svc.scan(f, false, null, null);
            assertEquals(1, r.comparison.modified.size());
            assertEquals(1, r.comparison.added.size());

            svc.createBaseline(f, null, null);
            assertFalse(svc.scan(f, false, null, null).comparison.hasChanges());
        }
    }

    @Test
    void duplicateFolderIsRejected(@TempDir Path tmp) throws Exception {
        Path watched = Files.createDirectory(tmp.resolve("w"));
        try (MonitorService svc = newService(tmp.resolve("home"))) {
            svc.addFolder(watched, FolderKind.GENERAL);
            assertThrows(IOException.class, () -> svc.addFolder(watched, FolderKind.BACKUP));
        }
    }

    @Test
    void tamperedBaselineRaisesCriticalAlert(@TempDir Path tmp) throws Exception {
        Path watched = Files.createDirectory(tmp.resolve("w"));
        Files.writeString(watched.resolve("a.txt"), "x");
        try (MonitorService svc = newService(tmp.resolve("home"))) {
            WatchedFolder f = svc.addFolder(watched, FolderKind.GENERAL);
            svc.scan(f, false, null, null);

            AtomicReference<AlertEvent> seen = new AtomicReference<>();
            svc.alerts().addListener(seen::set);
            Path bl = svc.baselinePath(f);
            Files.writeString(bl, Files.readString(bl).replace("a.txt", "z.txt"));

            assertThrows(BaselineTamperedException.class, () -> svc.scan(f, true, null, null));
            assertEquals(ChangeType.BASELINE_TAMPERED, seen.get().getType());
            assertEquals(Severity.CRITICAL, seen.get().getSeverity());
        }
    }

    @Test
    void realTimeMonitoringReportsAModifiedFile(@TempDir Path tmp) throws Exception {
        Path watched = Files.createDirectory(tmp.resolve("w"));
        Path file = watched.resolve("secret.txt");
        Files.writeString(file, "original");
        try (MonitorService svc = newService(tmp.resolve("home"))) {
            WatchedFolder f = svc.addFolder(watched, FolderKind.GENERAL);
            svc.scan(f, false, null, null);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<AlertEvent> seen = new AtomicReference<>();
            svc.alerts().addListener(ev -> { if (ev.getType() == ChangeType.MODIFIED) { seen.set(ev); latch.countDown(); } });

            svc.startMonitoring();
            Thread.sleep(500);
            Files.writeString(file, "tampered!");

            assertTrue(latch.await(10, TimeUnit.SECONDS), "no alert within 10s");
            assertTrue(seen.get().getPath().endsWith("secret.txt"));
            svc.stopMonitoring();
        }
    }

    @Test
    void restoringAFileProducesNoAlert(@TempDir Path tmp) throws Exception {
        Path watched = Files.createDirectory(tmp.resolve("w"));
        Path file = watched.resolve("a.txt");
        Files.writeString(file, "good");
        try (MonitorService svc = newService(tmp.resolve("home"))) {
            WatchedFolder f = svc.addFolder(watched, FolderKind.GENERAL);
            svc.scan(f, false, null, null);
            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
            svc.alerts().addListener(ev -> count.incrementAndGet());
            svc.startMonitoring();
            Thread.sleep(500);
            // edit and revert faster than the debounce window: content ends up identical to the baseline
            Files.writeString(file, "bad");
            Files.writeString(file, "good");
            Thread.sleep(1500);
            assertEquals(0, count.get());
        }
    }
}
