package com.sentinelfs.gui;

import com.sentinelfs.alert.ChangeType;
import com.sentinelfs.core.FileRecord;
import com.sentinelfs.integrity.IntegrityChecker.ComparisonResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/** Shows exactly what differs from the baseline and lets the user accept it as the new trusted state. */
public class ScanResultDialog extends JDialog {

    private record Row(ChangeType type, String path) {}

    public ScanResultDialog(Window owner, String folder, ComparisonResult r, Runnable onAccept) {
        super(owner, "Scan results", ModalityType.APPLICATION_MODAL);
        List<Row> rows = new ArrayList<>();
        for (FileRecord f : r.modified) rows.add(new Row(ChangeType.MODIFIED, f.getPath()));
        for (FileRecord f : r.added)    rows.add(new Row(ChangeType.ADDED, f.getPath()));
        for (FileRecord f : r.deleted)  rows.add(new Row(ChangeType.DELETED, f.getPath()));

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

        JPanel head = Widgets.transparent(new BorderLayout(0, 4));
        head.add(Widgets.label(r.changeCount() + " change" + (r.changeCount() == 1 ? "" : "s") + " detected", Theme.WARN, 18f, Font.BOLD), BorderLayout.NORTH);
        head.add(Widgets.label(folder + "   •   " + r.modified.size() + " modified, " + r.added.size() + " added, "
                + r.deleted.size() + " deleted, " + r.unchanged.size() + " unchanged", Theme.MUTED, 12f, Font.PLAIN), BorderLayout.SOUTH);
        root.add(head, BorderLayout.NORTH);

        JTable table = new JTable(new AbstractTableModel() {
            public int getRowCount() { return rows.size(); }
            public int getColumnCount() { return 2; }
            public String getColumnName(int c) { return c == 0 ? "Change" : "File"; }
            public Object getValueAt(int i, int c) { return c == 0 ? rows.get(i).type() : rows.get(i).path(); }
        });
        table.setDefaultRenderer(Object.class, new Widgets.CellRenderer());
        table.getColumnModel().getColumn(0).setMinWidth(120);
        table.getColumnModel().getColumn(0).setMaxWidth(120);
        table.getColumnModel().getColumn(0).setCellRenderer(new Widgets.CellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int row, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, row, c);
                if (v instanceof ChangeType ct) { comp.setForeground(Widgets.typeColor(ct)); comp.setFont(Theme.font(Font.BOLD, 12f)); }
                return comp;
            }
        });
        root.add(Widgets.styledTable(table), BorderLayout.CENTER);

        JButton accept = Widgets.success("These changes are OK — accept as new baseline");
        JButton close = Widgets.neutral("Close");
        accept.addActionListener(e -> { dispose(); onAccept.run(); });
        close.addActionListener(e -> dispose());
        JPanel buttons = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(close); buttons.add(accept);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        setPreferredSize(new Dimension(820, 480));
        pack();
        setLocationRelativeTo(owner);
    }
}
