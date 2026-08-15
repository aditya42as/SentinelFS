package com.sentinelfs.alert;

import com.sentinelfs.core.FileRecord;

public interface AlertListener {
    void onAlert(ChangeType type, FileRecord record);
}