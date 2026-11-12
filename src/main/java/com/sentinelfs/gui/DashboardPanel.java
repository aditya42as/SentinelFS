package com.sentinelfs.gui;

import com.sentinelfs.alert.AlertEvent;
import com.sentinelfs.alert.Severity;
import com.sentinelfs.config.WatchedFolder;
import com.sentinelfs.service.MonitorService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;

/** Overview: protection status, headline numbers, latest activity. */
public class DashboardPanel extends JPanel {
    private final MonitorService service;
    private final AlertTableModel all;
    private final AlertTableModel recent = new AlertTableModel(8);

    private final JLabel statusTitle = Widgets.label("", Theme.TEXT, 20f, Font.BOLD);
    private final JLabel statusSub = Widgets.label("", Theme.MUTED, 13f, Font.PLAIN);
    private final JLabel dot = new JLabel("●");
    private final JButton toggle = Widgets.primary("Start monitoring");
    private final Widgets.StatCard folders = new Widgets.StatCard("PROTECTED FOLDERS", Theme.ACCENT);
    private final Widgets.StatCard files = new Widgets.StatCard("FILES TRACKED", Theme.TEXT);
    private final Widgets.StatCard alerts24 = new Widgets.StatCard("ALERTS (LAST 24 H)", Theme.WARN);
    private final Widgets.StatCard critical = new Widgets.StatCard("CRITICAL (LAST 24 H)", Theme.CRIT);

    public DashboardPanel(MonitorService service, AlertTableModel all, Consumer<Boolean> onToggle) {
        this.service = service;
        this.all = all;
        setOpaque(false);
        setLayout(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));

        add(Widgets.titled("Dashboard", "Live overview of everything SentinelFS is protecting.", null), BorderLayout.NORTH);

        JPanel center = Widgets.transparent(new BorderLayout(0, 16));

        // hero
        Widgets.Card hero = new Widgets.Card();
        hero.setLayout(new BorderLayout(16, 0));
        dot.setFont(Theme.font(Font.PLAIN, 30f));
        JPanel text = Widgets.transparent(new BorderLayout(0, 4));
        text.add(statusTitle, BorderLayout.NORTH);
        text.add(statusSub, BorderLayout.CENTER);
        hero.add(dot, BorderLayout.WEST);
        hero.add(text, BorderLayout.CENTER);
        toggle.setPreferredSize(new Dimension(170, 40));
        toggle.addActionListener(e -> onToggle.accept(!service.isMonitoring()));
        JPanel btnWrap = Widgets.transparent(new java.awt.GridBagLayout());
        btnWrap.add(toggle);
        hero.add(btnWrap, BorderLayout.EAST);

        JPanel stats = Widgets.transparent(new GridLayout(1, 4, 14, 0));
        stats.add(folders); stats.add(files); stats.add(alerts24); stats.add(critical);

        JPanel upper = Widgets.transparent(new BorderLayout(0, 14));
        upper.add(hero, BorderLayout.NORTH);
        upper.add(stats, BorderLayout.CENTER);
        center.add(upper, BorderLayout.NORTH);

        // recent activity
        JPanel act = Widgets.transparent(new BorderLayout(0, 8));
        act.add(Widgets.label("Recent activity", Theme.TEXT, 15f, Font.BOLD), BorderLayout.NORTH);
        JTable t = new JTable(recent);
        AlertsPanel.styleAlertTable(t);
        AlertsPanel.fixWidths(t);
        act.add(Widgets.styledTable(t), BorderLayout.CENTER);
        center.add(act, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        all.addTableModelListener(e -> refresh());
        refresh();
    }

    public void onAlert(AlertEvent e) { recent.add(e); }

    public void loadRecent(java.util.List<AlertEvent> oldestFirst) {
        int from = Math.max(0, oldestFirst.size() - 8);
        recent.load(oldestFirst.subList(from, oldestFirst.size()));
    }

    public void refresh() {
        boolean on = service.isMonitoring();
        int nFolders = service.folders().size();
        dot.setForeground(on ? Theme.OK : Theme.MUTED);
        if (on) {
            statusTitle.setText("Real-time protection is ON");
            statusSub.setText("Watching " + nFolders + " folder" + (nFolders == 1 ? "" : "s") + " — you'll be alerted the moment a file changes.");
        } else {
            statusTitle.setText("Real-time protection is OFF");
            statusSub.setText(nFolders == 0 ? "Add a folder under \"Protected Folders\", then turn monitoring on."
                                            : "Turn monitoring on to be alerted instantly when files change.");
        }
        toggle.setText(on ? "Stop monitoring" : "Start monitoring");

        int fileTotal = 0;
        int issues = 0;
        for (WatchedFolder f : service.folders()) { fileTotal += f.fileCount(); issues += f.pendingChanges(); }
        folders.set(String.valueOf(nFolders), issues > 0 ? issues + " unreviewed change(s)" : "no open issues");
        files.set(String.format("%,d", fileTotal), "hashed with SHA-256");

        long since = System.currentTimeMillis() - 24L * 3600 * 1000;
        int a = 0, c = 0;
        for (AlertEvent ev : all.all()) {
            if (ev.getTime() < since) continue;
            a++;
            if (ev.getSeverity() == Severity.CRITICAL) c++;
        }
        alerts24.set(String.valueOf(a), "since yesterday");
        critical.set(String.valueOf(c), c == 0 ? "all quiet" : "needs attention");
        critical.setColor(c == 0 ? Theme.OK : Theme.CRIT);
    }
}
