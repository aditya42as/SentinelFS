package com.sentinelfs;

import java.nio.file.Path;

import com.sentinelfs.core.HashingUnit;

public class Main {

    public static void main(String[] args) {
        System.out.println("SentinelFS starting up...");

        try {
            
            String hash = HashingUnit.hashFile(Path.of("pom.xml"));
            System.out.println("SHA-256 of pom.xml: " + hash);
        } 
        catch (Exception e) {

            System.out.println("Something went wrong while hashing the file:");
            e.printStackTrace();
        }
    }
}