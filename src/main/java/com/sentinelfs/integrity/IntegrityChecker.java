package com.sentinelfs.integrity;

import com.sentinelfs.core.FileRecord;

import java.util.*;

import jdk.dynalink.linker.ConversionComparator;

public class IntegrityChecker {
    public static class ComparisonResult {

        public List<FileRecord> modified = new ArrayList<> ();
        public List<FileRecord> added = new ArrayList<> ();
        public List<FileRecord> deleted = new ArrayList<> ();
        public List<FileRecord> unchanged = new ArrayList<> ();
    }

    public static ComparisonResult compare(List<FileRecord> baseline, List<FileRecord> current) {
        ComparisonResult result = new ComparisonResult();

        Map<String, FileRecord> baselineByPath = new HashMap<>();
        for(FileRecord record : baseline) {
            baselineByPath.put(record.getPath(), record);
        }

        Map<String, FileRecord> currentByPath = new HashMap<>();
        for(FileRecord record : current) {
            currentByPath.put(record.getPath(), record);
        }

        for(FileRecord currentRecord: current) {
            FileRecord baselineRecord = baselineByPath.get(currentRecord.getPath());

            if(baselineRecord == null) {
                result.added.add(currentRecord);

            }
            else if (!baselineRecord.getHash().equals(currentRecord.getHash())) {
                result.modified.add(currentRecord);
            }
            else {
                result.unchanged.add(currentRecord);
            }
        }
        for(FileRecord baselineRecord : baseline) {
            if(!currentByPath.containsKey(baselineRecord.getPath())) {
                result.deleted.add(baselineRecord);
            }
        }
        return result;
    }
}