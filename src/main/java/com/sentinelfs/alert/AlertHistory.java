package com.sentinelfs.alert;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Persists every alert as one JSON line so history survives restarts. */
public class AlertHistory implements AlertListener {
    private static final Gson GSON = new Gson();
    private final Path file;

    public AlertHistory(Path file) { this.file = file; }

    @Override
    public synchronized void onAlert(AlertEvent e) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(e) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            System.err.println("Failed to persist alert: " + ex.getMessage());
        }
    }

    /** Most recent {@code max} alerts, oldest first. */
    public synchronized List<AlertEvent> loadRecent(int max) {
        List<AlertEvent> all = new ArrayList<>();
        if (!Files.exists(file)) return all;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    AlertEvent ev = GSON.fromJson(line, AlertEvent.class);
                    if (ev != null && ev.getType() != null && ev.getSeverity() != null) all.add(ev);
                } catch (RuntimeException ignored) { /* skip corrupt line */ }
            }
        } catch (IOException ex) {
            System.err.println("Could not read alert history: " + ex.getMessage());
        }
        return all.size() <= max ? all : new ArrayList<>(all.subList(all.size() - max, all.size()));
    }

    public synchronized void clear() {
        try { Files.deleteIfExists(file); } catch (IOException ex) { System.err.println(ex.getMessage()); }
    }
}
