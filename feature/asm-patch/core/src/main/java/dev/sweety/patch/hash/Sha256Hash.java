package dev.sweety.patch.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sha256Hash implements HashFunction {

    @Override
    public byte[] hash(byte[] data) {
        if (data == null) {
            return new byte[0];
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in the Java platform
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}