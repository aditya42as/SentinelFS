package com.sentinelfs.config;

import java.nio.file.Path;

/**
 * Where SentinelFS keeps its own data (config, baselines, alert history).
 * Defaults to ~/.sentinelfs; override with -Dsentinelfs.home=... (used by the tests).
 */
public final class AppPaths {
    private AppPaths() {}

    public static Path dataDir() {
        String override = System.getProperty("sentinelfs.home");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath();
        }
        return Path.of(System.getProperty("user.home"), ".sentinelfs");
    }

    public static Path baselinesDir() { return dataDir().resolve("baselines"); }
    public static Path configFile()   { return dataDir().resolve("config.json"); }
    public static Path historyFile()  { return dataDir().resolve("alert-history.jsonl"); }
    public static Path alertsLog()    { return dataDir().resolve("alerts.log"); }
    public static Path keyFile()      { return dataDir().resolve("seal.key"); }
}
