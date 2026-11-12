package com.sentinelfs.alert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Appends a human-readable line per alert to a plain-text log. */
public class FileLogListener implements AlertListener {
    private final Path logFile;

    public FileLogListener(Path logFile) { this.logFile = logFile; }

    @Override
    public synchronized void onAlert(AlertEvent e) {
        String ts = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.getTime()), ZoneId.systemDefault()).toString();
        String line = ts + " | " + e.getSeverity() + " | " + e.getType() + " | " + e.getPath() + System.lineSeparator();
        try {
            if (logFile.getParent() != null) Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            System.err.println("Failed to write log entry: " + ex.getMessage());
        }
    }
}
