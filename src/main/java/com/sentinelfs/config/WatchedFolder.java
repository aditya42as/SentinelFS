package com.sentinelfs.config;

import java.nio.file.Path;
import java.security.MessageDigest;

/** One protected folder plus a little bookkeeping that is persisted in config.json. */
public class WatchedFolder {
    private String path;
    private FolderKind kind = FolderKind.GENERAL;
    private long lastScanMillis;
    private int fileCount;

    // runtime-only state (not persisted)
    private transient String status = "Not scanned";
    private transient int pendingChanges;

    public WatchedFolder() {}

    public WatchedFolder(Path path, FolderKind kind) {
        this.path = path.toAbsolutePath().normalize().toString();
        this.kind = kind;
    }

    public Path path()        { return Path.of(path); }
    public String pathString(){ return path; }
    public FolderKind kind()  { return kind == null ? FolderKind.GENERAL : kind; }
    public void setKind(FolderKind k) { this.kind = k; }
    public long lastScanMillis() { return lastScanMillis; }
    public int fileCount()    { return fileCount; }
    public String status()    { return status; }
    public int pendingChanges() { return pendingChanges; }

    public void recordScan(int files) {
        this.fileCount = files;
        this.lastScanMillis = System.currentTimeMillis();
    }
    public void setStatus(String s, int pending) { this.status = s; this.pendingChanges = pending; }

    /** Stable, filesystem-safe id used to name this folder's baseline file. */
    public String baselineId() {
        String name = path().getFileName() == null ? "root" : path().getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return name + "-" + shortHash(path);
    }

    private static String shortHash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public String toString() { return path; }
}
