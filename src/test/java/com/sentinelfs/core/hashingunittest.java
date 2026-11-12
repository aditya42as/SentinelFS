package com.sentinelfs.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HashingUnitTest {

    @Test
    void sameContentProducesSameHash(@TempDir Path tempDir) throws IOException, NoSuchAlgorithmException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        String hash1 = HashingUnit.hashFile(file);
        String hash2 = HashingUnit.hashFile(file);

        assertEquals(hash1, hash2);
    }

    @Test
    void differentContentProducesDifferentHash(@TempDir Path tempDir) throws IOException, NoSuchAlgorithmException {
        Path file = tempDir.resolve("test.txt");

        Files.writeString(file, "hello world");
        String hash1 = HashingUnit.hashFile(file);

        Files.writeString(file, "hello world!"); // one character different
        String hash2 = HashingUnit.hashFile(file);

        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashIsAlwaysSameLengthForSha256(@TempDir Path tempDir) throws IOException, NoSuchAlgorithmException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "short");

        String hash = HashingUnit.hashFile(file);

        // SHA-256 always produces a 32-byte hash = 64 hex characters
        assertEquals(64, hash.length());
    }
}