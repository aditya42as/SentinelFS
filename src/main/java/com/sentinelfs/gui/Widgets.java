package com.sentinelfs.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Small reusable UI building blocks. */
public final class Widgets {
    private Widgets() {}

    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String formatTime(long millis) {
        if (millis <= 0) return "never";
        return TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    // ----------------------------------------------------------------- buttons
    public static class FlatButton extends JButton {
        private final Color base;
        private boolean hover;

        public FlatButton(String text, Color base) {
            super(text);
            this.base = base;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(Theme.font(Font.BOLD, 13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = !isEnabled() ? new Color(0x2B323D) : hover ? base.brighter() : base;
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            setForeground(isEnabled() ? Color.WHITE : Theme.MUTED);
            super.paintComponent(g);
        }
    }

    public static JButton primary(String text) { return new FlatButton(text, Theme.ACCENT); }
    public static JButton neutral(String text) { return new FlatButton(text, new Color(0x333B48)); }
    public static JButton danger(String text)  { return new FlatButton(text, new Color(0xB4343A)); }
    public static JButton success(String text) { return new FlatButton(text, new Color(0x1F9D55)); }

    // ----------------------------------------------------------------- cards
    public static class Card extends JPanel {
        public Card() {
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.CARD);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class StatCard extends Card {
        private final JLabel value = new JLabel("0");
        private final JLabel sub = new JLabel(" ");

        public StatCard(String title, Color accent) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            JLabel t = label(title, Theme.MUTED, 12f, Font.PLAIN);
            value.setFont(Theme.font(Font.BOLD, 30f));
            value.setForeground(accent);
            sub.setForeground(Theme.MUTED);
            sub.setFont(Theme.font(Font.PLAIN, 12f));
            t.setAlignmentX(LEFT_ALIGNMENT); value.setAlignmentX(LEFT_ALIGNMENT); sub.setAlignmentX(LEFT_ALIGNMENT);
            add(t); add(Box.createVerticalStrut(6)); add(value); add(Box.createVerticalStrut(2)); add(sub);
        }
        public void set(String v, String subtitle) { value.setText(v); sub.setText(subtitle); }
        public void setColor(Color c) { value.setForeground(c); }
    }

    public static JLabel label(String text, Color color, float size, int style) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(Theme.font(style, size));
        return l;
    }

    public static JLabel heading(String text) { return label(text, Theme.TEXT, 22f, Font.BOLD); }

    public static JPanel transparent(java.awt.LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    // ----------------------------------------------------------------- tables
    public static JScrollPane styledTable(JTable table) {
        table.setRowHeight(30);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(Theme.FIELD);
        table.setForeground(Theme.TEXT);
        table.setSelectionBackground(new Color(0x24477F));
        table.setSelectionForeground(Color.WHITE);
        JTableHeader h = table.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(10, 32));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, r, c);
                l.setBackground(Theme.CARD);
                l.setForeground(Theme.MUTED);
                l.setFont(Theme.font(Font.BOLD, 12f));
                l.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return l;
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Theme.FIELD);
        return sp;
    }

    /** Base renderer with padding; subclasses colour the text. */
    public static class CellRenderer extends DefaultTableCellRenderer {
        public CellRenderer() { setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10)); }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, false, r, c);
            l.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            if (!sel) {
                l.setBackground(r % 2 == 0 ? Theme.FIELD : new Color(0x191D23));
                l.setForeground(Theme.TEXT);
            }
            return l;
        }
    }

    public static Color severityColor(com.sentinelfs.alert.Severity s) {
        return switch (s) { case CRITICAL -> Theme.CRIT; case WARNING -> Theme.WARN; case INFO -> Theme.INFO; };
    }

    public static Color typeColor(com.sentinelfs.alert.ChangeType t) {
        return switch (t) {
            case MODIFIED -> Theme.WARN;
            case ADDED -> Theme.OK;
            case DELETED, BASELINE_TAMPERED -> Theme.CRIT;
        };
    }

    public static JPanel titled(String title, String subtitle, JComponent right) {
        JPanel p = transparent(new BorderLayout());
        JPanel left = transparent(new BorderLayout());
        left.add(heading(title), BorderLayout.NORTH);
        if (subtitle != null) left.add(label(subtitle, Theme.MUTED, 13f, Font.PLAIN), BorderLayout.SOUTH);
        p.add(left, BorderLayout.WEST);
        if (right != null) p.add(right, BorderLayout.EAST);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        return p;
    }

    public static JLabel centered(String text) {
        JLabel l = label(text, Theme.MUTED, 13f, Font.PLAIN);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }
}
