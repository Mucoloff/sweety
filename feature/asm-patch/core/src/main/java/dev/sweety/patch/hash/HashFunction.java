package dev.sweety.patch.hash;

public interface HashFunction {
    byte[] hash(byte[] data);

    default String calculateHash(byte[] data) {
        if (data == null) return null;
        return HashFunction.bytesToHex(hash(data));
    }

    static String bytesToHex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}