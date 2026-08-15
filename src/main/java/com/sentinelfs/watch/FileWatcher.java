package com.sentinelfs.watch;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.NoSuchAlgorithmException;

import com.sentinelfs.alert.AlertManager;
import com.sentinelfs.alert.ChangeType;
import com.sentinelfs.core.FileRecord;
import com.sentinelfs.core.HashingUnit;

public class FileWatcher {
    private final Path watchDir;
    private final AlertManager alertManager;

    public FileWatcher(Path watchDir, AlertManager alertManager) {
        this.watchDir = watchDir;
        this.alertManager = alertManager;
    }

    public void startWatching() throws IOException {
        WatchService watchService = FileSystems.getDefault().newWatchService();
        registerAll(watchDir, watchService);

        // watchDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        System.out.println("Watching " + watchDir + " for real time changes...");

        while (true) { 
            WatchKey key;
            try {
                key = watchService.take();
            } 
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            Path dirForKey = (Path) key.watchable();
            for(WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                Path changedFile = dirForKey.resolve((Path) event.context());
                handleEvent(kind, changedFile);

                if(kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changedFile)) {
                    try {
                        registerAll(changedFile, watchService);
                    } catch (IOException e) {
                        System.out.println("Could not watch new directory: " + changedFile);
                    }
                }
            }


            boolean valid = key.reset();
            if(!valid) break;   
        }
    }

    private void registerAll(Path start, WatchService watchService) throws IOException {
        Files.walk(start).filter(Files::isDirectory).forEach(dir -> {
            try {
                dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            } 
            catch (IOException e) {
                System.err.println("Could not register: " + dir);
            }
        });
    }

    private void handleEvent(WatchEvent.Kind<?> kind, Path changedFile) {
        try {
            if(kind == StandardWatchEventKinds.ENTRY_DELETE) {
                FileRecord record = new FileRecord(changedFile.toString(), "", 0, 0);
                alertManager.fireAlert(ChangeType.DELETED, record);
                return;
            }
            if(!Files.isRegularFile(changedFile)) {
                return;
            }
            String hash = HashingUnit.hashFile(changedFile);
            long size = Files.size(changedFile);
            long lastModified = Files.getLastModifiedTime(changedFile).toMillis();
            FileRecord record = new FileRecord(changedFile.toString(), hash, size, lastModified);
            ChangeType type = (kind == StandardWatchEventKinds.ENTRY_CREATE) ? ChangeType.ADDED : ChangeType.MODIFIED;
            alertManager.fireAlert(type, record);
        } 
        catch (IOException | NoSuchAlgorithmException e) {
            System.out.println("Could not process change for " + changedFile + ": " + e.getMessage());

        }
    }
}