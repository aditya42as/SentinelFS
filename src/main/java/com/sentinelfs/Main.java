package com.sentinelfs;

import com.sentinelfs.gui.MainWindow;
import com.sentinelfs.gui.Theme;
import com.sentinelfs.service.MonitorService;
import com.sentinelfs.ui.ConsoleController;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

/**
 * Entry point.
 *   (no args)  desktop app
 *   --cli      console menu (also used automatically on machines with no display)
 *   --version  print version
 */
public class Main {

    public static String version() {
        String v = Main.class.getPackage() == null ? null : Main.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    public static void main(String[] args) {
        boolean cli = false;
        for (String a : args) {
            if (a.equals("--version") || a.equals("-v")) {
                System.out.println("SentinelFS " + version());
                return;
            }
            if (a.equals("--cli")) cli = true;
        }
        if (cli || GraphicsEnvironment.isHeadless()) {
            if (!cli) System.out.println("No display detected - starting console mode.");
            new ConsoleController(new MonitorService()).run();
            return;
        }

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            MonitorService service = new MonitorService();
            MainWindow window = new MainWindow(service, version());
            window.setVisible(true);
            if (service.config().autoStartMonitoring && !service.folders().isEmpty()) {
                new Thread(service::startMonitoring, "sentinel-autostart").start();
            }
        });
    }
}
