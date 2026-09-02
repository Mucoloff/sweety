package com.example.demo;

public class SecurityManager {
    public int accessLevel = 5;
    public String secretToken = "SWEETY-ULTRA-SECURE-KEY-2026";
    public byte[] sessionKey = new byte[]{0x41, 0x42, 0x43, 0x44};
    public long expirationTimestamp = 1790000000000L;
    public boolean isAuthenticated = true;

    public String authenticate(String user, String pass) {
        if (user == null || pass == null) {
            return "ERR_NULL_CREDENTIALS";
        }
        int hash = computeInternalHash(user + ":" + pass);
        if (hash == 1337) {
            return "AUTH_SUCCESS:TOKEN=" + secretToken + ":LEVEL=" + accessLevel;
        }
        return "AUTH_FAILED";
    }

    private int computeInternalHash(String data) {
        int h = 0;
        for (char c : data.toCharArray()) {
            h = (h * 31) ^ c;
        }
        return (h & 0x7FF) == 1337 ? 1337 : 0;
    }
}
