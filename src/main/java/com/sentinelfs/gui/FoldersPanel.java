package com.sentinelfs.gui;

import com.sentinelfs.baseline.BaselineTamperedException;
import com.sentinelfs.config.FolderKind;
import com.sentinelfs.config.WatchedFolder;
import com.sentinelfs.integrity.ScanFiles;
import com.sentinelfs.service.MonitorService;
import com.sentinelfs.service.ScanResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manage protected folders: add/remove, scan, accept changes as the new baseline. */
public class FoldersPanel extends JPanel {
    private final MonitorService service;
    private final FolderTableModel model = new FolderTableModel();
    private final JTable table = new JTable(model);
    private final JLabel message = Widgets.label(" ", Theme.MUTED, 12f, Font.PLAIN);
    private final JProgressBar progress = new JProgressBar();
    private final List<JButton> actionButtons = new ArrayList<>();
    private final JButton cancel = Widgets.neutral("Cancel");
    private final JButton scanSel = Widgets.primary("Scan selected");
    private final JButton scanAll = Widgets.neutral("Scan all");
    private final JButton approve = Widgets.neutral("Accept current state");
    private final JButton remove = Widgets.danger("Remove");
    private final JButton add = Widgets.primary("+ Add folder");
    private volatile AtomicBoolean cancelFlag;

    public FoldersPanel(MonitorService service) {
        this.service = service;
        setOpaque(false);
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

        add(Widgets.titled("Protected Folders",
                "SentinelFS keeps a sealed SHA-256 baseline of each folder and flags anything that differs.", add), BorderLayout.NORTH);

        table.setDefaultRenderer(Object.class, new Widgets.CellRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new Widgets.CellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                String st = String.valueOf(v);
                Color col = Theme.TEXT;
                if (st.equals("Protected") || st.equals("Clean")) col = Theme.OK;
                else if (st.contains("change")) col = Theme.WARN;
                else if (st.contains("TAMPERED") || st.contains("missing") || st.startsWith("Error")) col = Theme.CRIT;
                else col = Theme.MUTED;
                comp.setForeground(col);
                comp.setFont(Theme.font(Font.BOLD, 12f));
                return comp;
            }
        });
        table.getColumnModel().getColumn(0).setPreferredWidth(380);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(70);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.getColumnModel().getColumn(4).setPreferredWidth(150);
        add(Widgets.styledTable(table), BorderLayout.CENTER);

        JPanel actions = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(scanSel); actions.add(scanAll); actions.add(approve); actions.add(remove);
        actionButtons.add(scanSel); actionButtons.add(scanAll); actionButtons.add(approve); actionButtons.add(remove); actionButtons.add(add);

        progress.setVisible(false);
        progress.setStringPainted(true);
        JPanel status = Widgets.transparent(new BorderLayout(10, 0));
        status.add(message, BorderLayout.CENTER);
        JPanel pr = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        cancel.setVisible(false);
        pr.add(progress); pr.add(cancel);
        status.add(pr, BorderLayout.EAST);

        JPanel south = Widgets.transparent(new BorderLayout(0, 8));
        south.add(actions, BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        add.addActionListener(e -> addFolder());
        remove.addActionListener(e -> removeSelected());
        scanSel.addActionListener(e -> { WatchedFolder f = selected(); if (f != null) runScan(List.of(f)); });
        scanAll.addActionListener(e -> runScan(new ArrayList<>(service.folders())));
        approve.addActionListener(e -> { WatchedFolder f = selected(); if (f != null) approveBaseline(f); });
        cancel.addActionListener(e -> { AtomicBoolean c = cancelFlag; if (c != null) c.set(true); });

        service.addStateListener(() -> SwingUtilities.invokeLater(model::fireTableDataChanged));
        updateEnabled(false);
        table.getSelectionModel().addListSelectionListener(e -> updateEnabled(false));
    }

    private WatchedFolder selected() {
        int r = table.getSelectedRow();
        if (r < 0 || r >= service.folders().size()) {
            JOptionPane.showMessageDialog(this, "Select a folder in the table first.");
            return null;
        }
        return service.folders().get(r);
    }

    private void updateEnabled(boolean busy) {
        boolean sel = table.getSelectedRow() >= 0;
        add.setEnabled(!busy);
        scanAll.setEnabled(!busy && !service.folders().isEmpty());
        scanSel.setEnabled(!busy && sel);
        approve.setEnabled(!busy && sel);
        remove.setEnabled(!busy && sel);
    }

    private void setBusy(boolean busy, String text) {
        updateEnabled(busy);
        progress.setVisible(busy);
        cancel.setVisible(busy);
        progress.setIndeterminate(busy);
        progress.setString(text);
        if (!busy) cancelFlag = null;
    }

    private void note(String text, Color c) {
        message.setForeground(c);
        message.setText(text);
    }

    // ------------------------------------------------------------ actions
    private void addFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Choose a folder to protect");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        JComboBox<FolderKind> kind = new JComboBox<>(FolderKind.values());
        int ok = JOptionPane.showConfirmDialog(this, new Object[]{
                "What is in this folder?\n(Logs and backups raise CRITICAL alerts on any modification or deletion.)", kind},
                "Folder type", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            WatchedFolder f = service.addFolder(fc.getSelectedFile().toPath(), (FolderKind) kind.getSelectedItem());
            model.fireTableDataChanged();
            runScan(List.of(f));   // creates the first baseline
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Could not add folder", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void removeSelected() {
        WatchedFolder f = selected();
        if (f == null) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Stop protecting\n" + f.pathString() + "\nand delete its baseline?\n(Your files are not touched.)",
                "Remove folder", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            service.removeFolder(f);
            model.fireTableDataChanged();
            note("Removed " + f.pathString(), Theme.MUTED);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void approveBaseline(WatchedFolder f) {
        int ok = JOptionPane.showConfirmDialog(this,
                "Treat the current contents of\n" + f.pathString() + "\nas the new trusted baseline?",
                "Accept current state", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) acceptBaseline(f);
    }

    private void acceptBaseline(WatchedFolder f) {
        AtomicBoolean cancelToken = new AtomicBoolean();
        cancelFlag = cancelToken;
        setBusy(true, "Hashing…");
        new SwingWorker<Integer, Integer>() {
            @Override protected Integer doInBackground() throws Exception {
                return service.createBaseline(f, n -> publish(n), cancelToken).size();
            }
            @Override protected void process(List<Integer> chunks) { progress.setString(String.format("%,d files", chunks.get(chunks.size() - 1))); }
            @Override protected void done() {
                setBusy(false, "");
                try {
                    note("Baseline updated: " + String.format("%,d", get()) + " files tracked in " + f.pathString(), Theme.OK);
                } catch (Exception ex) { reportFailure(ex); }
                model.fireTableDataChanged();
            }
        }.execute();
    }

    /** Scans folders one after another, showing a result dialog for any that changed. */
    private void runScan(List<WatchedFolder> folders) {
        if (folders.isEmpty()) return;
        AtomicBoolean cancelToken = new AtomicBoolean();
        cancelFlag = cancelToken;
        setBusy(true, "Scanning…");
        new SwingWorker<List<ScanResult>, Integer>() {
            private final List<String> errors = new ArrayList<>();
            @Override protected List<ScanResult> doInBackground() {
                List<ScanResult> out = new ArrayList<>();
                for (WatchedFolder f : folders) {
                    if (cancelToken.get()) break;
                    try {
                        out.add(service.scan(f, true, n -> publish(n), cancelToken));
                    } catch (ScanFiles.ScanCancelledException ex) {
                        break;
                    } catch (BaselineTamperedException ex) {
                        errors.add("⚠ " + ex.getMessage());
                    } catch (IOException ex) {
                        errors.add(f.pathString() + ": " + ex.getMessage());
                    }
                }
                return out;
            }
            @Override protected void process(List<Integer> chunks) { progress.setString(String.format("%,d files", chunks.get(chunks.size() - 1))); }
            @Override protected void done() {
                setBusy(false, "");
                model.fireTableDataChanged();
                try {
                    List<ScanResult> results = get();
                    int changes = 0, created = 0;
                    for (ScanResult r : results) { changes += r.changeCount(); if (r.baselineCreated) created++; }
                    if (!errors.isEmpty()) {
                        note(String.join("   |   ", errors), Theme.CRIT);
                    } else if (cancelToken.get()) {
                        note("Scan cancelled.", Theme.MUTED);
                    } else if (changes > 0) {
                        note(changes + " change(s) found.", Theme.WARN);
                    } else if (created > 0) {
                        note("Baseline created for " + created + " folder(s). Turn monitoring on to watch for changes.", Theme.OK);
                    } else {
                        note("Scan complete — no changes. Everything matches the baseline.", Theme.OK);
                    }
                    for (ScanResult r : results) {
                        if (r.comparison != null && r.comparison.hasChanges()) {
                            new ScanResultDialog(SwingUtilities.getWindowAncestor(FoldersPanel.this), r.folder.pathString(),
                                    r.comparison, () -> acceptBaseline(r.folder)).setVisible(true);
                        }
                    }
                } catch (Exception ex) { reportFailure(ex); }
            }
        }.execute();
    }

    private void reportFailure(Exception ex) {
        Throwable t = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
        note("Failed: " + t.getMessage(), Theme.CRIT);
    }

    // ------------------------------------------------------------ table model
    private class FolderTableModel extends AbstractTableModel {
        private final String[] cols = {"Folder", "Type", "Files", "Last scan", "Status"};
        public int getRowCount() { return service.folders().size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }
        public Object getValueAt(int r, int c) {
            WatchedFolder f = service.folders().get(r);
            return switch (c) {
                case 0 -> f.pathString();
                case 1 -> f.kind().toString();
                case 2 -> f.fileCount() == 0 ? "—" : String.format("%,d", f.fileCount());
                case 3 -> Widgets.formatTime(f.lastScanMillis());
                default -> f.status();
            };
        }
    }
}
