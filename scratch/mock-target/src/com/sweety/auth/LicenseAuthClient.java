package com.sweety.auth;

public class LicenseAuthClient {
    public int licenseTier = 3;
    public String masterKey = "SWEETY-MASTER-ENTERPRISE-KEY-994821";
    public byte[] sessionSalt = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
    public long validityTimestamp = 1890000000000L;
    public boolean isVip = true;

    public boolean verifyMasterLicense(String inputKey) {
        if (inputKey == null || inputKey.length() < 10) {
            return false;
        }
        int h = 0;
        for (char c : inputKey.toCharArray()) {
            h = (h * 31) ^ c;
        }
        return ((h ^ 0x5A5A) & 0x7FF) == 0x1337 && inputKey.equals(masterKey);
    }

    public String generateSessionToken(String username) {
        if (username == null) return "ERR_INVALID_USER";
        return "TOKEN_" + username + "_" + masterKey.hashCode() + "_VALID";
    }
}
