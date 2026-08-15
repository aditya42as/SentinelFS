package com.sentinelfs.baseline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sentinelfs.core.FileRecord;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BaselineStore {
    private static  final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public  static void save(List<FileRecord> records, Path baselineFile) throws IOException {
        String json = gson.toJson(records);
        Files.writeString(baselineFile, json);    
    }

    public static List<FileRecord> load(Path baselineFile) throws IOException {
        String json = Files.readString(baselineFile);
        Type listType = new TypeToken<List<FileRecord>>() {}.getType();
        return gson.fromJson(json, listType);
    }
}
