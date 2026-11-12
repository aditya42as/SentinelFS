package com.sentinelfs.integrity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class IgnoreMatcherTest {
    private final Path root = Path.of("/data/project").toAbsolutePath();

    @Test
    void matchesFileNameGlob() {
        IgnoreMatcher m = new IgnoreMatcher(List.of("*.tmp"));
        assertTrue(m.isIgnored(root, root.resolve("a/b/scratch.tmp")));
        assertFalse(m.isIgnored(root, root.resolve("a/b/scratch.txt")));
    }

    @Test
    void ignoredFolderNameCoversEverythingInside() {
        IgnoreMatcher m = new IgnoreMatcher(List.of("node_modules"));
        assertTrue(m.isIgnored(root, root.resolve("web/node_modules/lib/index.js")));
        assertFalse(m.isIgnored(root, root.resolve("web/src/index.js")));
    }

    @Test
    void blankLinesCommentsAndBadPatternsAreHarmless() {
        IgnoreMatcher m = new IgnoreMatcher(List.of("", "   ", "# comment", "[unclosed"));
        assertFalse(m.isIgnored(root, root.resolve("anything.txt")));
    }

    @Test
    void rootItselfIsNeverIgnored() {
        IgnoreMatcher m = new IgnoreMatcher(List.of("*"));
        assertFalse(m.isIgnored(root, root));
    }
}
