package com.sentinelfs.alert;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Observer-pattern hub: detection code fires events, any number of listeners react. Thread-safe. */
public class AlertManager {
    private final List<AlertListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(AlertListener listener) { listeners.add(listener); }
    public void removeListener(AlertListener listener) { listeners.remove(listener); }

    public void fireAlert(AlertEvent event) {
        for (AlertListener l : listeners) {
            try {
                l.onAlert(event);
            } catch (RuntimeException e) {
                System.err.println("Alert listener failed: " + e);
            }
        }
    }
}
