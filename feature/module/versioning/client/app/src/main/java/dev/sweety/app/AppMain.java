package dev.sweety.app;

import dev.sweety.build.BuildInfo;

public class AppMain {
    public static void main(String[] args) {
        System.out.println("Hello from App!");
        System.out.println("BuildId=" + BuildInfo.BUILD_ID);
        System.out.println("ChannelId=" + BuildInfo.CHANNEL);
        System.out.println("ClientId=" + BuildInfo.CLIENT_ID);
        System.out.println("Version=" + BuildInfo.VERSION);
        System.out.println("Timestamp=" + BuildInfo.TIMESTAMP);
        new ExampleDiff().exampleMethod();
    }
}