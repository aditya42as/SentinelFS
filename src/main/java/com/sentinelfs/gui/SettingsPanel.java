package com.sentinelfs.gui;

import com.sentinelfs.config.AppConfig;
import com.sentinelfs.config.AppPaths;
import com.sentinelfs.service.MonitorService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SettingsPanel extends JPanel {
    private final MonitorService service;
    private final JTextArea ignore = new JTextArea(8, 30);
    private final JSpinner interval = new JSpinner(new SpinnerNumberModel(60, 0, 1440, 5));
    private final JCheckBox autoStart = new JCheckBox("Start real-time monitoring when SentinelFS opens");
    private final JCheckBox notify = new JCheckBox("Show desktop notifications for warnings and critical alerts");
    private final JCheckBox tray = new JCheckBox("Keep running in the system tray when the window is closed");

    public SettingsPanel(MonitorService service, String version) {
        this.service = service;
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));
        add(Widgets.titled("Settings", "Tune what is ignored and how SentinelFS behaves.", null), BorderLayout.NORTH);

        JPanel col = Widgets.transparent(null);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        Widgets.Card c1 = new Widgets.Card();
        c1.setLayout(new BorderLayout(0, 8));
        c1.add(Widgets.label("Ignore patterns", Theme.TEXT, 15f, Font.BOLD), BorderLayout.NORTH);
        ignore.setBackground(Theme.FIELD);
        ignore.setForeground(Theme.TEXT);
        ignore.setCaretColor(Theme.TEXT);
        ignore.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        ignore.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane sp = new JScrollPane(ignore);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        c1.add(sp, BorderLayout.CENTER);
        c1.add(Widgets.label("One glob per line, e.g. *.log, node_modules, build/*.o. Matches file names, folder names and relative paths.",
                Theme.MUTED, 12f, Font.PLAIN), BorderLayout.SOUTH);

        Widgets.Card c2 = new Widgets.Card();
        c2.setLayout(new BoxLayout(c2, BoxLayout.Y_AXIS));
        JPanel row = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.add(Widgets.label("Full scan every", Theme.TEXT, 13f, Font.PLAIN));
        row.add(interval);
        row.add(Widgets.label("minutes while monitoring is on (0 = off). A safety net in case the OS drops events.", Theme.MUTED, 12f, Font.PLAIN));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        for (JCheckBox cb : new JCheckBox[]{autoStart, notify, tray}) { cb.setOpaque(false); cb.setForeground(Theme.TEXT); cb.setAlignmentX(LEFT_ALIGNMENT); }
        c2.add(row); c2.add(Box.createVerticalStrut(8));
        c2.add(autoStart); c2.add(notify); c2.add(tray);

        Widgets.Card c3 = new Widgets.Card();
        c3.setLayout(new BorderLayout(10, 0));
        JPanel info = Widgets.transparent(new BorderLayout(0, 4));
        info.add(Widgets.label("Data folder", Theme.TEXT, 15f, Font.BOLD), BorderLayout.NORTH);
        info.add(Widgets.label(AppPaths.dataDir() + "   •   SentinelFS " + version, Theme.MUTED, 12f, Font.PLAIN), BorderLayout.SOUTH);
        c3.add(info, BorderLayout.CENTER);
        JButton open = Widgets.neutral("Open folder");
        open.addActionListener(e -> {
            try { java.nio.file.Files.createDirectories(AppPaths.dataDir()); Desktop.getDesktop().open(AppPaths.dataDir().toFile()); }
            catch (IOException | UnsupportedOperationException ex) { JOptionPane.showMessageDialog(this, AppPaths.dataDir().toString()); }
        });
        c3.add(open, BorderLayout.EAST);

        for (Widgets.Card c : new Widgets.Card[]{c1, c2, c3}) { c.setAlignmentX(LEFT_ALIGNMENT); }
        c1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        c2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        c3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        col.add(c1); col.add(Box.createVerticalStrut(14)); col.add(c2); col.add(Box.createVerticalStrut(14)); col.add(c3);
        add(col, BorderLayout.CENTER);

        JButton save = Widgets.primary("Save settings");
        save.addActionListener(e -> save());
        JPanel south = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        south.add(save);
        add(south, BorderLayout.SOUTH);

        load();
    }

    private void load() {
        AppConfig c = service.config();
        ignore.setText(String.join("\n", c.ignorePatterns));
        ignore.setCaretPosition(0);
        interval.setValue(c.scanIntervalMinutes);
        autoStart.setSelected(c.autoStartMonitoring);
        notify.setSelected(c.desktopNotifications);
        tray.setSelected(c.minimizeToTray);
    }

    private void save() {
        AppConfig c = service.config();
        List<String> patterns = new ArrayList<>();
        for (String line : ignore.getText().split("\\R")) if (!line.isBlank()) patterns.add(line.trim());
        c.ignorePatterns = patterns;
        c.scanIntervalMinutes = (Integer) interval.getValue();
        c.autoStartMonitoring = autoStart.isSelected();
        c.desktopNotifications = notify.isSelected();
        c.minimizeToTray = tray.isSelected();
        try {
            service.saveConfig();
            JOptionPane.showMessageDialog(this, "Saved.\nNew ignore patterns apply to the next scan; restart monitoring to apply them to live watching.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
