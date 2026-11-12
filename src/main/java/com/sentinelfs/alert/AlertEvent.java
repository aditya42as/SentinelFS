package com.sentinelfs.alert;

import com.sentinelfs.config.FolderKind;
import com.sentinelfs.core.FileRecord;

/** An immutable record of one detected change. */
public class AlertEvent {
    private final long time;
    private final ChangeType type;
    private final Severity severity;
    private final String path;
    private final String folder;
    private final String hash;

    public AlertEvent(long time, ChangeType type, Severity severity, String path, String folder, String hash) {
        this.time = time;
        this.type = type;
        this.severity = severity;
        this.path = path;
        this.folder = folder;
        this.hash = hash == null ? "" : hash;
    }

    public static AlertEvent of(ChangeType type, FileRecord record, String folder, FolderKind kind) {
        return new AlertEvent(System.currentTimeMillis(), type, severityFor(type, kind),
                record.getPath(), folder, record.getHash());
    }

    public static Severity severityFor(ChangeType type, FolderKind kind) {
        if (type == ChangeType.BASELINE_TAMPERED) return Severity.CRITICAL;
        boolean strict = kind == FolderKind.LOG || kind == FolderKind.BACKUP;
        return switch (type) {
            case ADDED -> Severity.INFO;
            case MODIFIED, DELETED -> strict ? Severity.CRITICAL : Severity.WARNING;
            default -> Severity.WARNING;
        };
    }

    public long getTime()        { return time; }
    public ChangeType getType()  { return type; }
    public Severity getSeverity(){ return severity; }
    public String getPath()      { return path; }
    public String getFolder()    { return folder; }
    public String getHash()      { return hash; }

    @Override public String toString() { return severity + " " + type + ": " + path; }
}
