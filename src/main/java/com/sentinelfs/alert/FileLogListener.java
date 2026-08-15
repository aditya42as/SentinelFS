package com.sentinelfs.alert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import com.sentinelfs.core.FileRecord;

public class FileLogListener implements AlertListener {
    private final Path logFile;

    public FileLogListener(Path logFile) {
        this.logFile = logFile;
    } 

    @Override
    public void onAlert(ChangeType type, FileRecord record) {
        String line = LocalDateTime.now() + " | " + type + " | " + record.getPath() + System.lineSeparator();
        try{
            Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch(IOException e) {
            System.out.println("Failed to write log entry: " + e.getMessage());
        }
    }
}