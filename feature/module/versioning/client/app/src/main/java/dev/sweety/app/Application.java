package dev.sweety.app;

import dev.sweety.build.BuildInfo;
import dev.sweety.versioning.lifecycle.Lifecycle;

/**
 * Standard client application runtime implementing {@link Lifecycle}.
 */
public class Application implements Lifecycle {

    private static volatile Application instance;

    public static Application getInstance() {
        if (instance == null) {
            synchronized (Application.class) {
                if (instance == null) {
                    instance = new Application();
                }
            }
        }
        return instance;
    }

    @Override
    public void load() {
        System.out.println("Loading Application v" + BuildInfo.VERSION + " (" + BuildInfo.CHANNEL + ")");
    }

    @Override
    public void start() {
        System.out.println("Application started successfully [Build " + BuildInfo.BUILD_ID + "]");
    }

    @Override
    public void shutdown() {
        System.out.println("Application shutting down...");
    }

    public static void main(String[] args) {
        Application app = getInstance();
        try {
            app.load();
            app.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
