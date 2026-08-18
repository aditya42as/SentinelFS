package com.sentinelfs.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

import com.sentinelfs.alert.AlertManager;
import com.sentinelfs.alert.ConsoleAlertListener;
import com.sentinelfs.alert.FileLogListener;
import com.sentinelfs.baseline.BaselineStore;
import com.sentinelfs.core.FileRecord;
import com.sentinelfs.integrity.IntegrityChecker;
import com.sentinelfs.integrity.ScanFiles;
import com.sentinelfs.watch.FileWatcher;


public class ConsoleController {
    private final Scanner scanner = new Scanner(System.in);
    private final AlertManager alertManager = new AlertManager();

    private Path monitoredDir = null;
    private Path baselineFile = null;

    public ConsoleController() {
        alertManager.addListener(new ConsoleAlertListener());
        alertManager.addListener(new FileLogListener(Path.of("alerts.log")));
    }

    public void run() {
        System.out.println("Sentinel FS");
        boolean running = true;
        while(running) {
            printMenu();
            String choice = scanner.nextLine().trim();

            switch(choice) {
                case "1" -> addMonitoredFolder();
                case "2" -> runIntegrityCheck();
                case "3" -> approveBaseline();
                case "4" -> startRealTimeMonitoring();
                case "5" -> running = false;
                default -> System.out.println("Not a valid option. Try Again");

            }
        }
        System.out.println("Exiting");
    }
    private void printMenu() {
        System.out.println("\n1. Add a folder to monitor");
        System.out.println("2. Run Integrity Check");
        System.out.println("3. Approve current state as new baseline");
        System.out.println("4. Start Real Time Monitoring(30 sec demo)");
        System.out.println("5. Exit");
        System.out.println("Choose an Option: ");
    }
    private void addMonitoredFolder() {
        System.out.print("Enter path folder to monitor: ");
        String input = scanner.nextLine().trim();
        Path path = Path.of(input);

        if(!Files.exists(path)) {
            System.out.println("That path doesn't exist.");
            return;
        }
        monitoredDir = path;
        baselineFile = Path.of("baseline_" + path.getFileName() + ".json");
        System.out.println("Now Monitoring: " + monitoredDir);
    }

    private void runIntegrityCheck() {
        if (monitoredDir == null) {
            System.out.println("No folder set yet — choose option 1 first.");
            return;
        }

        try {
            List<FileRecord> current = ScanFiles.scanDir(monitoredDir);

            if (!Files.exists(baselineFile)) {
                System.out.println("No baseline exists yet — creating one now.");
                BaselineStore.save(current, baselineFile);
                System.out.println("Baseline saved with " + current.size() + " files.");
                return;
            }

            List<FileRecord> baseline = BaselineStore.load(baselineFile);
            IntegrityChecker.ComparisonResult result = IntegrityChecker.compare(baseline, current);

            System.out.println("Modified: " + result.modified.size());
            System.out.println("Added: " + result.added.size());
            System.out.println("Deleted: " + result.deleted.size());
            System.out.println("Unchanged: " + result.unchanged.size());

            IntegrityChecker.reportAlerts(result, alertManager);

        }
        catch (IOException e) {
            System.out.println("Scan failed: " + e.getMessage());
        }
    }

    private void approveBaseline() {
        if(monitoredDir == null) {
            System.out.println("No folder set yet - choose option 1 first.");
            return;
        }

        try {
            List<FileRecord> current = ScanFiles.scanDir(monitoredDir);
            BaselineStore.save(current, baselineFile);
            System.out.println("Baseline updated with current state (" + current.size() + " files.");    
        } 
        catch (IOException e) {
            System.out.println("Could not update baseline: " + e.getMessage());
        }
    }

    private void startRealTimeMonitoring() {
        if(monitoredDir == null) {
            System.out.println("No folder set yet - choose option 1 first.");
            return;
        }
        FileWatcher watcher = new FileWatcher(monitoredDir, alertManager);
        Thread watchThread = new Thread(() -> {
            try {
                watcher.startWatching();
            } 
            catch (IOException e) {
                System.out.println("Watcher failed: " + e.getMessage());
            }
        });
        watchThread.setDaemon(true);
        watchThread.start();

        System.out.println("Watching for 30 seconds - try editing a file now.");
        try {
            Thread.sleep(30000);
        } 
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Demo window closed. Returning to menu.");
    }
}