package com.sentinelfs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ConfigStore() {}

    public static AppConfig load() {
        Path f = AppPaths.configFile();
        if (Files.exists(f)) {
            try {
                AppConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), AppConfig.class);
                if (c != null) {
                    if (c.folders == null) c.folders = new java.util.ArrayList<>();
                    if (c.ignorePatterns == null) c.ignorePatterns = new java.util.ArrayList<>(AppConfig.defaultIgnorePatterns());
                    return c;
                }
            } catch (Exception e) {
                System.err.println("Could not read config, using defaults: " + e.getMessage());
            }
        }
        return new AppConfig();
    }

    public static void save(AppConfig cfg) throws IOException {
        Path f = AppPaths.configFile();
        Files.createDirectories(f.getParent());
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(cfg), StandardCharsets.UTF_8);
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
    }
}
