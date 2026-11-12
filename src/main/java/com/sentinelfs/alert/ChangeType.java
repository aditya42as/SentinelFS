package com.sentinelfs.alert;

public enum ChangeType {
    MODIFIED,
    ADDED,
    DELETED,
    /** The stored baseline itself failed its integrity seal. */
    BASELINE_TAMPERED
}
