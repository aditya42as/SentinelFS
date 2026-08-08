package com.sentinelfs;

import java.nio.file.Path;
import java.util.List;

import com.sentinelfs.core.FileRecord;
import com.sentinelfs.integrity.ScanFiles;

public class Main {

    public static void main(String[] args) {
        System.out.println("SentinelFS starting up...");

        try {
            System.out.println("Scanning: " + Path.of("src").toAbsolutePath());
            List<FileRecord> records = ScanFiles.scanDir(Path.of("src"));
            System.out.println("Scanned " + records.size() + " files:");
            for (FileRecord record : records) {
                System.out.println(record);
            }
        } catch (Exception e) {
            System.out.println("Scan failed:");
            e.printStackTrace();
        }
    }
}