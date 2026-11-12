package com.sentinelfs.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** The app icon, drawn in code so the project has no binary assets to manage. */
public final class Icons {
    private Icons() {}

    public static BufferedImage shield(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size;
        Path2D p = new Path2D.Double();
        p.moveTo(s * 0.50, s * 0.06);
        p.curveTo(s * 0.68, s * 0.17, s * 0.82, s * 0.18, s * 0.90, s * 0.19);
        p.curveTo(s * 0.90, s * 0.60, s * 0.74, s * 0.82, s * 0.50, s * 0.95);
        p.curveTo(s * 0.26, s * 0.82, s * 0.10, s * 0.60, s * 0.10, s * 0.19);
        p.curveTo(s * 0.18, s * 0.18, s * 0.32, s * 0.17, s * 0.50, s * 0.06);
        p.closePath();
        g.setPaint(new GradientPaint(0, 0, new Color(0x60A5FA), 0, size, new Color(0x2563EB)));
        g.fill(p);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) (s * 0.09), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D tick = new Path2D.Double();
        tick.moveTo(s * 0.32, s * 0.50);
        tick.lineTo(s * 0.45, s * 0.63);
        tick.lineTo(s * 0.69, s * 0.37);
        g.draw(tick);
        g.dispose();
        return img;
    }

    public static List<java.awt.Image> appIcons() {
        List<java.awt.Image> l = new ArrayList<>();
        for (int s : new int[]{16, 32, 64, 128, 256}) l.add(shield(s));
        return l;
    }
}
