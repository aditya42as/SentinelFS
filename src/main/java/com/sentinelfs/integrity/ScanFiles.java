package com.sentinelfs.integrity;

import java.io.IOException;
import  java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.sentinelfs.core.FileRecord;
import com.sentinelfs.core.HashingUnit;

public class ScanFiles {

    private static FileRecord buildRecord(Path filePath) throws IOException, NoSuchAlgorithmException {
        String hash = HashingUnit.hashFile(filePath);
        long size = Files.size(filePath);
        long lastModified = Files.getLastModifiedTime(filePath).toMillis();

        return new FileRecord(filePath.toString(), hash, size, lastModified);
    }

    public static List<FileRecord> scanDir(Path root) throws IOException {
        
        List<FileRecord> records =  new ArrayList<>();

        try(Stream<Path> paths = Files.walk(root)) {
            List<Path> filePaths = paths.filter(Files::isRegularFile).toList();

            for(Path filePath : filePaths) {
                try {
                    FileRecord record = buildRecord(filePath);
                    records.add(record);
                } 
                catch (IOException | NoSuchAlgorithmException e) {
                    System.out.println("Skipping unreadable file: " + filePath + " (" + e.getMessage() + ")");
                }
            }

        }
        return records;
    }

}