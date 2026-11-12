package com.sentinelfs.gui;

import com.sentinelfs.alert.AlertEvent;
import com.sentinelfs.alert.Severity;
import com.sentinelfs.service.MonitorService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainWindow extends JFrame {
    private static final String DASH = "dash", FOLDERS = "folders", ALERTS = "alerts", SETTINGS = "settings";

    private final MonitorService service;
    private final AlertTableModel alertModel = new AlertTableModel(5000);
    private final DashboardPanel dashboard;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, NavButton> nav = new LinkedHashMap<>();
    private final JLabel statusBar = Widgets.label(" ", Theme.MUTED, 12f, Font.PLAIN);
    private TrayIcon trayIcon;
    private MenuItem trayToggle;
    private int unread;
    private String current = DASH;

    public MainWindow(MonitorService service, String version) {
        super("SentinelFS");
        this.service = service;
        setIconImages(Icons.appIcons());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setPreferredSize(new Dimension(1120, 720));

        alertModel.load(service.history().loadRecent(5000));
        dashboard = new DashboardPanel(service, alertModel, this::setMonitoring);
        dashboard.loadRecent(service.history().loadRecent(8));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(buildSidebar(version), BorderLayout.WEST);

        content.setOpaque(false);
        content.add(dashboard, DASH);
        content.add(new FoldersPanel(service), FOLDERS);
        content.add(new AlertsPanel(service, alertModel), ALERTS);
        content.add(new SettingsPanel(service, version), SETTINGS);
        root.add(content, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.SIDEBAR);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        bar.add(statusBar, BorderLayout.WEST);
        root.add(bar, BorderLayout.SOUTH);
        setContentPane(root);

        service.alerts().addListener(ev -> SwingUtilities.invokeLater(() -> onAlert(ev)));
        service.addStateListener(() -> SwingUtilities.invokeLater(this::refreshState));

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onClose(); }
        });
        setupTray();
        show(DASH);
        refreshState();
        pack();
        setLocationRelativeTo(null);
    }

    // ----------------------------------------------------------- sidebar
    private JPanel buildSidebar(String version) {
        JPanel side = new JPanel();
        side.setBackground(Theme.SIDEBAR);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(208, 10));
        side.setBorder(BorderFactory.createEmptyBorder(22, 14, 16, 14));

        JLabel logo = new JLabel("SentinelFS", new javax.swing.ImageIcon(Icons.shield(30)), SwingConstants.LEFT);
        logo.setIconTextGap(10);
        logo.setForeground(Theme.TEXT);
        logo.setFont(Theme.font(Font.BOLD, 18f));
        logo.setAlignmentX(LEFT_ALIGNMENT);
        side.add(logo);
        side.add(Box.createVerticalStrut(28));

        addNav(side, DASH, "Dashboard");
        addNav(side, FOLDERS, "Protected Folders");
        addNav(side, ALERTS, "Alerts");
        addNav(side, SETTINGS, "Settings");
        side.add(Box.createVerticalGlue());

        JLabel v = Widgets.label("File integrity monitor  •  v" + version, Theme.MUTED, 11f, Font.PLAIN);
        v.setAlignmentX(LEFT_ALIGNMENT);
        side.add(v);
        return side;
    }

    private void addNav(JPanel side, String id, String text) {
        NavButton b = new NavButton(text);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.addActionListener(e -> show(id));
        nav.put(id, b);
        side.add(b);
        side.add(Box.createVerticalStrut(4));
    }

    private void show(String id) {
        current = id;
        cards.show(content, id);
        nav.forEach((k, b) -> b.setSelected(k.equals(id)));
        if (id.equals(ALERTS)) { unread = 0; updateAlertsBadge(); }
    }

    private void updateAlertsBadge() {
        nav.get(ALERTS).setText(unread > 0 ? "Alerts  (" + unread + ")" : "Alerts");
    }

    // ----------------------------------------------------------- behaviour
    private void setMonitoring(boolean on) {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                if (on) service.startMonitoring(); else service.stopMonitoring();
                return null;
            }
        }.execute();
    }

    private void onAlert(AlertEvent ev) {
        alertModel.add(ev);
        dashboard.onAlert(ev);
        if (!current.equals(ALERTS) || !isActive()) { unread++; updateAlertsBadge(); }
        boolean important = ev.getSeverity() != Severity.INFO;
        if (trayIcon != null && service.config().desktopNotifications && important && (!isVisible() || !isActive())) {
            String name = java.nio.file.Path.of(ev.getPath()).getFileName() == null ? ev.getPath()
                    : java.nio.file.Path.of(ev.getPath()).getFileName().toString();
            trayIcon.displayMessage("SentinelFS — " + ev.getSeverity() + ": " + ev.getType(), name + "\n" + ev.getFolder(),
                    ev.getSeverity() == Severity.CRITICAL ? TrayIcon.MessageType.ERROR : TrayIcon.MessageType.WARNING);
        }
    }

    private void refreshState() {
        dashboard.refresh();
        int n = service.folders().size();
        statusBar.setText((service.isMonitoring() ? "● Monitoring " + n + " folder(s) in real time" : "○ Monitoring is off")
                + "     •     data: " + com.sentinelfs.config.AppPaths.dataDir());
        statusBar.setForeground(service.isMonitoring() ? Theme.OK : Theme.MUTED);
        if (trayToggle != null) trayToggle.setLabel(service.isMonitoring() ? "Stop monitoring" : "Start monitoring");
        if (trayIcon != null) trayIcon.setToolTip("SentinelFS — " + (service.isMonitoring() ? "protection ON" : "protection OFF"));
    }

    private void setupTray() {
        if (!SystemTray.isSupported()) return;
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open SentinelFS");
        open.addActionListener(e -> restore());
        trayToggle = new MenuItem("Start monitoring");
        trayToggle.addActionListener(e -> setMonitoring(!service.isMonitoring()));
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> quit());
        menu.add(open); menu.add(trayToggle); menu.addSeparator(); menu.add(exit);
        trayIcon = new TrayIcon(Icons.shield(32), "SentinelFS", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> restore());
        try { SystemTray.getSystemTray().add(trayIcon); } catch (AWTException e) { trayIcon = null; }
    }

    private void restore() {
        SwingUtilities.invokeLater(() -> { setVisible(true); setExtendedState(NORMAL); toFront(); requestFocus(); });
    }

    private void onClose() {
        if (trayIcon != null && service.config().minimizeToTray) {
            setVisible(false);
            if (service.isMonitoring()) {
                trayIcon.displayMessage("SentinelFS is still running", "Real-time protection stays on. Use the tray icon to reopen or exit.", TrayIcon.MessageType.INFO);
            }
            return;
        }
        if (service.isMonitoring()) {
            int r = JOptionPane.showConfirmDialog(this, "Real-time protection will stop if you exit. Exit anyway?", "Exit SentinelFS",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
        }
        quit();
    }

    public void quit() {
        if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon);
        service.close();
        dispose();
        System.exit(0);
    }

    // ----------------------------------------------------------- nav button
    private static class NavButton extends JButton {
        private boolean selected, hover;
        NavButton(String text) {
            super(text);
            setHorizontalAlignment(SwingConstants.LEFT);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(Theme.font(Font.PLAIN, 14f));
            setForeground(Theme.MUTED);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 12));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override public void setSelected(boolean s) { selected = s; setForeground(s ? Color.WHITE : Theme.MUTED); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected) {
                g2.setColor(new Color(0x1D2B45));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(0, 8, 4, getHeight() - 16, 4, 4);
            } else if (hover) {
                g2.setColor(new Color(0x181C23));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
