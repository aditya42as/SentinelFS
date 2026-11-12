package com.sentinelfs.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

import com.sentinelfs.alert.AlertListener;
import com.sentinelfs.alert.ConsoleAlertListener;
import com.sentinelfs.config.FolderKind;
import com.sentinelfs.config.WatchedFolder;
import com.sentinelfs.integrity.IntegrityChecker;
import com.sentinelfs.service.MonitorService;
import com.sentinelfs.service.ScanResult;

/** Text front-end on top of the same engine the GUI uses (handy for servers / SSH). */
public class ConsoleController {
    private final Scanner in = new Scanner(System.in);
    private final MonitorService service;

    public ConsoleController(MonitorService service) {
        this.service = service;
    }

    public void run() {
        System.out.println("SentinelFS (console mode)");
        boolean running = true;
        while (running) {
            System.out.println("\n1. Add a folder to protect");
            System.out.println("2. List protected folders");
            System.out.println("3. Run integrity check");
            System.out.println("4. Accept current state as new baseline");
            System.out.println("5. Start real-time monitoring (press Enter to stop)");
            System.out.println("6. Exit");
            System.out.print("Choose an option: ");
            if (!in.hasNextLine()) break;
            switch (in.nextLine().trim()) {
                case "1" -> addFolder();
                case "2" -> list();
                case "3" -> check();
                case "4" -> approve();
                case "5" -> monitor();
                case "6" -> running = false;
                default -> System.out.println("Not a valid option.");
            }
        }
        service.close();
        System.out.println("Bye.");
    }

    private void addFolder() {
        System.out.print("Folder path: ");
        Path p = Path.of(in.nextLine().trim());
        System.out.print("Type [1=General, 2=Logs, 3=Backups] (default 1): ");
        String t = in.nextLine().trim();
        FolderKind kind = t.equals("2") ? FolderKind.LOG : t.equals("3") ? FolderKind.BACKUP : FolderKind.GENERAL;
        try {
            WatchedFolder f = service.addFolder(p, kind);
            ScanResult r = service.scan(f, false, null, null);
            System.out.println("Protecting " + f + " - baseline of " + r.current.size() + " files created.");
        } catch (IOException e) {
            System.out.println("Could not add folder: " + e.getMessage());
        }
    }

    private void list() {
        List<WatchedFolder> fs = service.folders();
        if (fs.isEmpty()) System.out.println("No folders yet.");
        for (int i = 0; i < fs.size(); i++) {
            WatchedFolder f = fs.get(i);
            System.out.printf("%d. %s  [%s]  %d files  - %s%n", i + 1, f.pathString(), f.kind(), f.fileCount(), f.status());
        }
    }

    private WatchedFolder pick() {
        list();
        if (service.folders().isEmpty()) return null;
        System.out.print("Folder number: ");
        try {
            int i = Integer.parseInt(in.nextLine().trim()) - 1;
            return service.folders().get(i);
        } catch (RuntimeException e) {
            System.out.println("Invalid choice.");
            return null;
        }
    }

    private void check() {
        WatchedFolder f = pick();
        if (f == null) return;
        try {
            ScanResult r = service.scan(f, true, null, null);
            if (r.baselineCreated) { System.out.println("No baseline existed - created one (" + r.current.size() + " files)."); return; }
            IntegrityChecker.ComparisonResult c = r.comparison;
            System.out.printf("Modified: %d  Added: %d  Deleted: %d  Unchanged: %d%n",
                    c.modified.size(), c.added.size(), c.deleted.size(), c.unchanged.size());
        } catch (IOException e) {
            System.out.println("Scan failed: " + e.getMessage());
        }
    }

    private void approve() {
        WatchedFolder f = pick();
        if (f == null) return;
        try {
            int n = service.createBaseline(f, null, null).size();
            System.out.println("Baseline updated (" + n + " files).");
        } catch (IOException e) {
            System.out.println("Could not update baseline: " + e.getMessage());
        }
    }

    private void monitor() {
        if (service.folders().isEmpty()) { System.out.println("Add a folder first."); return; }
        AlertListener console = new ConsoleAlertListener();
        service.alerts().addListener(console);
        service.startMonitoring();
        System.out.println("Monitoring " + service.folders().size() + " folder(s). Press Enter to stop.");
        in.nextLine();
        service.stopMonitoring();
        service.alerts().removeListener(console);
        System.out.println("Stopped.");
    }
}
