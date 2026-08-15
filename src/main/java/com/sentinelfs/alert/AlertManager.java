package com.sentinelfs.alert;

import java.util.ArrayList;
import java.util.List;

import com.sentinelfs.core.FileRecord;

public class AlertManager {
    private final List<AlertListener> listeners = new ArrayList<>();
    public void addListener(AlertListener listener) {
        listeners.add(listener);
    }

    public void fireAlert(ChangeType type, FileRecord record) {
        for(AlertListener listener : listeners) {
            listener.onAlert(type, record);
        }
    }
}
