package com.sentinelfs.integrity;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

/**
 * Glob-based exclusions (e.g. "*.tmp", ".git", "build/*.o").
 * A pattern matches if it matches the file name, any parent folder name, or the relative path.
 */
public class IgnoreMatcher {
    public static final IgnoreMatcher NONE = new IgnoreMatcher(List.of());

    private final List<PathMatcher> matchers = new ArrayList<>();

    public IgnoreMatcher(List<String> patterns) {
        for (String p : patterns) {
            String t = p == null ? "" : p.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            try {
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + t));
            } catch (IllegalArgumentException e) {
                System.err.println("Ignoring invalid pattern '" + t + "'");
            }
        }
    }

    public boolean isIgnored(Path root, Path file) {
        if (matchers.isEmpty()) return false;
        Path rel;
        try {
            rel = root.relativize(file);
        } catch (IllegalArgumentException e) {
            rel = file.getFileName() == null ? file : file.getFileName();
        }
        if (rel.toString().isEmpty()) return false;
        for (PathMatcher m : matchers) {
            if (m.matches(rel)) return true;
            for (Path part : rel) {
                if (m.matches(part)) return true;
            }
        }
        return false;
    }
}
