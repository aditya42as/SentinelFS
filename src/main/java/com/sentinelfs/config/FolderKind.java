package com.sentinelfs.config;

/** What a protected folder contains. Drives how serious a change is considered. */
public enum FolderKind {
    GENERAL("General files"),
    LOG("Archived logs"),
    BACKUP("Backups");

    private final String label;
    FolderKind(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
