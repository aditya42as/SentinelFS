package com.sentinelfs.alert;

import com.sentinelfs.core.FileRecord;
//testing
public class ConsoleAlertListener implements AlertListener {

    @Override
    public void onAlert(ChangeType type, FileRecord record) {
        System.out.println("[ALERT] " + type + ": " + record.getPath());
    }
}