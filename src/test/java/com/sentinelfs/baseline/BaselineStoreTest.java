package com.sentinelfs.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sentinelfs.core.FileRecord;

class BaselineStoreTest {

    private static List<FileRecord> sample() {
        return List.of(new FileRecord("/x/a.txt", "abc", 3, 100), new FileRecord("/x/b.txt", "def", 4, 200));
    }

    @Test
    void roundTrips(@TempDir Path dir) throws IOException {
        System.setProperty("sentinelfs.home", dir.resolve("home").toString());
        Path f = dir.resolve("baseline.json");
        BaselineStore.save(sample(), f);
        List<FileRecord> back = BaselineStore.load(f);
        assertEquals(2, back.size());
        assertEquals("abc", back.get(0).getHash());
    }

    @Test
    void editedBaselineIsRejected(@TempDir Path dir) throws IOException {
        System.setProperty("sentinelfs.home", dir.resolve("home").toString());
        Path f = dir.resolve("baseline.json");
        BaselineStore.save(sample(), f);
        Files.writeString(f, Files.readString(f).replace("abc", "evil"));
        assertThrows(BaselineTamperedException.class, () -> BaselineStore.load(f));
    }

    @Test
    void missingSealIsRejected(@TempDir Path dir) throws IOException {
        System.setProperty("sentinelfs.home", dir.resolve("home").toString());
        Path f = dir.resolve("baseline.json");
        BaselineStore.save(sample(), f);
        Files.delete(dir.resolve("baseline.json.sig"));
        assertThrows(BaselineTamperedException.class, () -> BaselineStore.load(f));
    }
}
