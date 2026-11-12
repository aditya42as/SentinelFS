package com.sentinelfs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HashingKnownVectorTest {
    @Test
    void sha256OfAbcMatchesPublishedVector(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("abc.txt");
        Files.writeString(f, "abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HashingUnit.hashFile(f));
    }
}
