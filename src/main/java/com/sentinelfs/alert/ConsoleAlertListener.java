package com.sentinelfs.alert;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ConsoleAlertListener implements AlertListener {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void onAlert(AlertEvent e) {
        String t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.getTime()), ZoneId.systemDefault()).format(FMT);
        System.out.println("[" + t + "] [" + e.getSeverity() + "] " + e.getType() + ": " + e.getPath());
    }
}
