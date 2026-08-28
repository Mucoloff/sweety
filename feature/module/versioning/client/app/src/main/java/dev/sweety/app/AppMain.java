package dev.sweety.app;

import dev.sweety.build.BuildInfo;

public final class AppMain {

    private AppMain() {}

    public static void main(String[] args) {
        System.out.println("client " + BuildInfo.VERSION + " (" + BuildInfo.CHANNEL + ", build " + BuildInfo.BUILD_ID + ")");
    }
}
