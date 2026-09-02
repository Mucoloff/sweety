package com.example.demo;

public class App {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("🚀 [SAMPLE APP] Starting client application...");
        
        SecurityManager sec = new SecurityManager();
        System.out.println("🔑 [SAMPLE APP] Initial secret token: " + sec.secretToken);
        
        String authResult = sec.authenticate("admin", "secret123");
        System.out.println("🛡 [SAMPLE APP] Auth result: " + authResult);
        
        System.out.println("✅ [SAMPLE APP] Application finished successfully!");
        System.out.println("==================================================");
    }
}
