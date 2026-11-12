package com.sentinelfs.gui;

import com.sentinelfs.alert.AlertEvent;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Newest alert first. Capped so a noisy folder can't eat all memory. */
public class AlertTableModel extends AbstractTableModel {
    private static final String[] COLS = {"Time", "Severity", "Change", "File", "Protected folder"};
    private final List<AlertEvent> rows = new ArrayList<>();
    private final int cap;

    public AlertTableModel(int cap) { this.cap = cap; }

    public void add(AlertEvent e) {
        rows.add(0, e);
        fireTableRowsInserted(0, 0);
        if (rows.size() > cap) {
            int last = rows.size() - 1;
            rows.remove(last);
            fireTableRowsDeleted(last, last);
        }
    }

    /** @param oldestFirst events as returned by AlertHistory */
    public void load(List<AlertEvent> oldestFirst) {
        rows.clear();
        for (int i = oldestFirst.size() - 1; i >= 0 && rows.size() < cap; i--) rows.add(oldestFirst.get(i));
        fireTableDataChanged();
    }

    public void clear() { rows.clear(); fireTableDataChanged(); }
    public AlertEvent get(int i) { return rows.get(i); }
    public List<AlertEvent> all() { return rows; }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLS.length; }
    @Override public String getColumnName(int c) { return COLS[c]; }

    @Override public Object getValueAt(int r, int c) {
        AlertEvent e = rows.get(r);
        return switch (c) {
            case 0 -> Widgets.formatTime(e.getTime());
            case 1 -> e.getSeverity();
            case 2 -> e.getType();
            case 3 -> e.getPath();
            default -> e.getFolder();
        };
    }
}
