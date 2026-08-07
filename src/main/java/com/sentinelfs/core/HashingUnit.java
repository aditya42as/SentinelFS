package com.sentinelfs.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashingUnit {
    public static final String DEFAULT_ALGORITHM = "SHA-256";

    //read file in chunks instead of the whole files or blocks into memory
    private static final int BUFFER_SIZE = 8192;

    public static String hashFile(Path filePath) throws IOException, NoSuchAlgorithmException {
        return hashFile(filePath, DEFAULT_ALGORITHM);
    }

    public static String hashFile(Path filePath, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest Digest = MessageDigest.getInstance(algorithm);
        try(InputStream in = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while((bytesRead = in.read(buffer)) != -1) {
                Digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = Digest.digest();
        return  bytesToHex(hashBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for(byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if(hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return  sb.toString();
    }
}