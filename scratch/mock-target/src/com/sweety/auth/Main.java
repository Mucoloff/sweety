package com.sweety.auth;

public class Main {
    public static void main(String[] args) {
        System.out.println(">>> Starting Sweety License Auth Client...");
        LicenseAuthClient client = new LicenseAuthClient();
        boolean valid = client.verifyMasterLicense("SWEETY-MASTER-ENTERPRISE-KEY-994821");
        System.out.println(">>> License Verification: " + (valid ? "AUTHORIZED" : "DENIED"));
        System.out.println(">>> Session Token: " + client.generateSessionToken("admin"));
    }
}
