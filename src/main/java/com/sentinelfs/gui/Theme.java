package com.sentinelfs.gui;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Font;
import java.util.Enumeration;

/** Dark colour palette + Nimbus look-and-feel tweaks (no third-party UI library needed). */
public final class Theme {
    private Theme() {}

    public static final Color BG        = new Color(0x13161B);
    public static final Color SIDEBAR   = new Color(0x0E1014);
    public static final Color CARD      = new Color(0x1B1F26);
    public static final Color BORDER    = new Color(0x2A303A);
    public static final Color FIELD     = new Color(0x161A20);
    public static final Color TEXT      = new Color(0xE6E9EF);
    public static final Color MUTED     = new Color(0x8B94A5);
    public static final Color ACCENT    = new Color(0x3B82F6);
    public static final Color OK        = new Color(0x22C55E);
    public static final Color WARN      = new Color(0xF59E0B);
    public static final Color CRIT      = new Color(0xEF4444);
    public static final Color INFO      = new Color(0x60A5FA);

    public static final String FONT_FAMILY = "Segoe UI";

    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, 1).deriveFont(style, size);
    }

    public static void install() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); break; }
            }
        } catch (Exception ignored) { }

        UIManager.put("control", CARD);
        UIManager.put("info", CARD);
        UIManager.put("nimbusBase", new Color(0x2B323D));
        UIManager.put("nimbusBlueGrey", new Color(0x2B323D));
        UIManager.put("nimbusLightBackground", FIELD);
        UIManager.put("text", TEXT);
        UIManager.put("nimbusSelectionBackground", ACCENT);
        UIManager.put("nimbusSelectedText", Color.WHITE);
        UIManager.put("nimbusFocus", ACCENT);
        UIManager.put("nimbusDisabledText", MUTED);
        UIManager.put("nimbusBorder", BORDER);
        UIManager.put("Table.alternateRowColor", new Color(0x191D23));
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.showGrid", Boolean.FALSE);
        UIManager.put("ScrollBar.thumb", new Color(0x3A4250));
        UIManager.put("ToolTip.background", CARD);
        UIManager.put("ToolTip.foreground", TEXT);

        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (key.toString().endsWith(".font")) UIManager.put(key, new FontUIResource(font(Font.PLAIN, 13f)));
        }
        UIManager.put("defaultFont", new FontUIResource(font(Font.PLAIN, 13f)));
    }
}
