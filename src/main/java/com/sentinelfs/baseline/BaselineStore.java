package com.sentinelfs.baseline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sentinelfs.config.AppPaths;
import com.sentinelfs.core.FileRecord;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;

/**
 * Saves/loads baselines as JSON. Every baseline is "sealed" with an HMAC-SHA256 stored in a
 * sidecar {@code .sig} file, so editing the baseline to hide a change is detected on load.
 * The key lives in the SentinelFS data folder.
 */
public class BaselineStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<FileRecord>>() {}.getType();

    private BaselineStore() {}

    public static void save(List<FileRecord> records, Path baselineFile) throws IOException {
        if (baselineFile.toAbsolutePath().getParent() != null) {
            Files.createDirectories(baselineFile.toAbsolutePath().getParent());
        }
        byte[] json = GSON.toJson(records).getBytes(StandardCharsets.UTF_8);
        Path tmp = baselineFile.resolveSibling(baselineFile.getFileName() + ".tmp");
        Files.write(tmp, json);
        Files.move(tmp, baselineFile, StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(sigFile(baselineFile), hmacHex(json), StandardCharsets.UTF_8);
    }

    public static List<FileRecord> load(Path baselineFile) throws IOException {
        byte[] json = Files.readAllBytes(baselineFile);
        Path sig = sigFile(baselineFile);
        if (!Files.exists(sig)) {
            throw new BaselineTamperedException("Baseline has no integrity seal: " + baselineFile.getFileName());
        }
        String expected = Files.readString(sig, StandardCharsets.UTF_8).trim();
        // constant-time compare
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), hmacHex(json).getBytes(StandardCharsets.UTF_8))) {
            throw new BaselineTamperedException("Baseline does not match its integrity seal: " + baselineFile.getFileName());
        }
        List<FileRecord> list = GSON.fromJson(new String(json, StandardCharsets.UTF_8), LIST_TYPE);
        return list == null ? new java.util.ArrayList<>() : list;
    }

    public static boolean exists(Path baselineFile) { return Files.exists(baselineFile); }

    public static void delete(Path baselineFile) throws IOException {
        Files.deleteIfExists(baselineFile);
        Files.deleteIfExists(sigFile(baselineFile));
    }

    private static Path sigFile(Path baselineFile) {
        return baselineFile.resolveSibling(baselineFile.getFileName() + ".sig");
    }

    private static String hmacHex(byte[] data) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(loadOrCreateKey(), "HmacSHA256"));
            byte[] out = mac.doFinal(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IOException("HMAC unavailable", e);
        }
    }

    private static synchronized byte[] loadOrCreateKey() throws IOException {
        Path keyFile = AppPaths.keyFile();
        if (Files.exists(keyFile)) return Files.readAllBytes(keyFile);
        Files.createDirectories(keyFile.getParent());
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Files.write(keyFile, key);
        try { // best effort: owner-only on POSIX systems
            Files.setPosixFilePermissions(keyFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) { }
        return key;
    }
}
