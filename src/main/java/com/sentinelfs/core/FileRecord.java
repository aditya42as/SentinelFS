package com.sentinelfs.core;

public class FileRecord{
    private final String path;
    private final String hash;
    private final long sizeofFile;
    private final long lastModified;

    public FileRecord(String path, String hash, long sizeofFile, long lastModified) {
        this.path = path;
        this.hash = hash;
        this.sizeofFile = sizeofFile;
        this.lastModified = lastModified;
    }

    public String getPath() {
        return path;
    }
    public String getHash() {
        return hash;
    }
    public long  getSizeofFile() {
        return sizeofFile;
    }
    public long getLastModified() {
        return  lastModified;
    }

    @Override
    public String toString() {
        return "File Record {Path = '" + path +"', hash = '" + hash + "', sizeofFile = '" + sizeofFile + "', Last Modified = '" + lastModified + "'}";

    }
}