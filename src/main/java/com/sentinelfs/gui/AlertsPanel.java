package com.sentinelfs.gui;

import com.sentinelfs.alert.AlertEvent;
import com.sentinelfs.alert.ChangeType;
import com.sentinelfs.alert.Severity;
import com.sentinelfs.service.MonitorService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Full alert history with filtering, CSV export and "open location". */
public class AlertsPanel extends JPanel {
    private final MonitorService service;
    private final AlertTableModel model;
    private final JTable table;
    private final TableRowSorter<AlertTableModel> sorter;
    private final JComboBox<String> severityBox = new JComboBox<>();
    private final JComboBox<String> typeBox = new JComboBox<>();
    private final JTextField search = new JTextField(18);
    private final JLabel count = Widgets.label(" ", Theme.MUTED, 12f, Font.PLAIN);

    public AlertsPanel(MonitorService service, AlertTableModel model) {
        this.service = service;
        this.model = model;
        setOpaque(false);
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

        JButton export = Widgets.neutral("Export CSV");
        JButton clear = Widgets.danger("Clear history");
        export.addActionListener(e -> exportCsv());
        clear.addActionListener(e -> clearHistory());
        JPanel buttons = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(export); buttons.add(clear);

        JPanel top = Widgets.transparent(new BorderLayout());
        top.add(Widgets.titled("Alerts", "Every change SentinelFS has detected. Double-click a row to open its folder.", buttons), BorderLayout.NORTH);

        severityBox.addItem("All severities");
        for (Severity s : Severity.values()) severityBox.addItem(s.name());
        typeBox.addItem("All changes");
        for (ChangeType t : ChangeType.values()) typeBox.addItem(t.name());
        search.setToolTipText("Filter by file path");
        search.setBackground(Theme.FIELD);
        search.setForeground(Theme.TEXT);
        search.setCaretColor(Theme.TEXT);
        severityBox.setPreferredSize(new Dimension(150, 28));
        typeBox.setPreferredSize(new Dimension(160, 28));
        search.setPreferredSize(new Dimension(240, 28));
        JPanel filters = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.add(severityBox); filters.add(typeBox); filters.add(Widgets.label("Search file:", Theme.MUTED, 12f, Font.PLAIN)); filters.add(search); filters.add(count);
        top.add(filters, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        table = new JTable(model);
        styleAlertTable(table);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        fixWidths(table);
        add(Widgets.styledTable(table), BorderLayout.CENTER);

        Runnable refilter = this::applyFilter;
        severityBox.addActionListener(e -> refilter.run());
        typeBox.addActionListener(e -> refilter.run());
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refilter.run(); }
            public void removeUpdate(DocumentEvent e) { refilter.run(); }
            public void changedUpdate(DocumentEvent e) { refilter.run(); }
        });
        model.addTableModelListener(e -> updateCount());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) openLocation(); }
        });
        updateCount();
    }

    /** Fixed widths for the short columns so nothing is cut off; file path takes the rest. */
    static void fixWidths(JTable t) {
        int[] w = {160, 100, 120};
        for (int i = 0; i < w.length; i++) {
            t.getColumnModel().getColumn(i).setMinWidth(w[i]);
            t.getColumnModel().getColumn(i).setMaxWidth(w[i]);
        }
        t.getColumnModel().getColumn(3).setPreferredWidth(600);
        t.getColumnModel().getColumn(4).setPreferredWidth(200);
    }

    /** Shared by the dashboard's "recent activity" table. */
    public static void styleAlertTable(JTable t) {
        t.setDefaultRenderer(Object.class, new Widgets.CellRenderer());
        t.getColumnModel().getColumn(1).setCellRenderer(new Widgets.CellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                java.awt.Component comp = super.getTableCellRendererComponent(tb, v, s, f, r, c);
                if (v instanceof Severity sev) { comp.setForeground(Widgets.severityColor(sev)); comp.setFont(Theme.font(Font.BOLD, 12f)); }
                return comp;
            }
        });
        t.getColumnModel().getColumn(2).setCellRenderer(new Widgets.CellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                java.awt.Component comp = super.getTableCellRendererComponent(tb, v, s, f, r, c);
                if (v instanceof ChangeType ct) comp.setForeground(Widgets.typeColor(ct));
                return comp;
            }
        });
    }

    private void applyFilter() {
        List<RowFilter<AlertTableModel, Integer>> fs = new ArrayList<>();
        if (severityBox.getSelectedIndex() > 0) {
            String s = (String) severityBox.getSelectedItem();
            fs.add(new RowFilter<>() {
                @Override public boolean include(Entry<? extends AlertTableModel, ? extends Integer> e) {
                    return String.valueOf(e.getValue(1)).equals(s);
                }
            });
        }
        if (typeBox.getSelectedIndex() > 0) {
            String s = (String) typeBox.getSelectedItem();
            fs.add(new RowFilter<>() {
                @Override public boolean include(Entry<? extends AlertTableModel, ? extends Integer> e) {
                    return String.valueOf(e.getValue(2)).equals(s);
                }
            });
        }
        String q = search.getText().trim().toLowerCase();
        if (!q.isEmpty()) {
            fs.add(new RowFilter<>() {
                @Override public boolean include(Entry<? extends AlertTableModel, ? extends Integer> e) {
                    return String.valueOf(e.getValue(3)).toLowerCase().contains(q);
                }
            });
        }
        sorter.setRowFilter(fs.isEmpty() ? null : RowFilter.andFilter(fs));
        updateCount();
    }

    private void updateCount() {
        count.setText("  " + table.getRowCount() + " of " + model.getRowCount() + " shown");
    }

    private void openLocation() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        AlertEvent ev = model.get(table.convertRowIndexToModel(row));
        Path p = Path.of(ev.getPath());
        Path dir = Files.isDirectory(p) ? p : p.getParent();
        while (dir != null && !Files.isDirectory(dir)) dir = dir.getParent();
        if (dir != null && Desktop.isDesktopSupported()) {
            try { Desktop.getDesktop().open(dir.toFile()); } catch (IOException | UnsupportedOperationException ignored) { }
        }
    }

    private void exportCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("sentinelfs-alerts.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        StringBuilder sb = new StringBuilder("time,severity,change,file,folder,sha256\n");
        for (int i = 0; i < table.getRowCount(); i++) {
            AlertEvent e = model.get(table.convertRowIndexToModel(i));
            sb.append(csv(Widgets.formatTime(e.getTime()))).append(',').append(e.getSeverity()).append(',')
              .append(e.getType()).append(',').append(csv(e.getPath())).append(',')
              .append(csv(e.getFolder())).append(',').append(e.getHash()).append('\n');
        }
        try {
            Files.writeString(fc.getSelectedFile().toPath(), sb.toString(), StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Exported " + table.getRowCount() + " alerts.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not write file: " + ex.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String csv(String s) { return '"' + s.replace("\"", "\"\"") + '"'; }

    private void clearHistory() {
        int ok = JOptionPane.showConfirmDialog(this, "Delete all stored alert history?", "Clear history",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) { service.history().clear(); model.clear(); }
    }
}
