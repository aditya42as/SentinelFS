package com.sentinelfs.config;

import java.util.ArrayList;
import java.util.List;

/** User settings, persisted as JSON in the data directory. */
public class AppConfig {
    public List<WatchedFolder> folders = new ArrayList<>();
    public List<String> ignorePatterns = new ArrayList<>(defaultIgnorePatterns());
    /** Minutes between automatic full scans while monitoring is on. 0 = off. */
    public int scanIntervalMinutes = 60;
    public boolean autoStartMonitoring = true;
    public boolean desktopNotifications = true;
    public boolean minimizeToTray = true;

    public static List<String> defaultIgnorePatterns() {
        return List.of("*.tmp", "*.temp", "*.swp", "*.part", "*.crdownload", "~$*",
                "Thumbs.db", "desktop.ini", ".DS_Store", ".git", "node_modules");
    }
}
