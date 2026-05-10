package dev.sweety.launcher;

import dev.sweety.versioning.version.artifact.Artifact;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MainLauncher {

    public static void main(String[] args) throws Exception {
        final Path configFile = Path.of("config.json");
        final Path appJar = Path.of("app.jar");
        final Path selfJar = Path.of("launcher.jar");

        Map<Artifact, Path> artifacts = new HashMap<>();
        artifacts.put(Artifact.APP, appJar);
        artifacts.put(Artifact.LAUNCHER, selfJar);

        final SweetyLauncher launcher = new SweetyLauncher(configFile, artifacts);

        launcher.setHandshakeListener(state -> {
            if (state == null) return;
            switch (state) {
                case UNAVAILABLE -> System.out.println("Update server is currently unavailable.");
                case UP_TO_DATE -> System.out.println("You are up to date!");
                case UPDATED -> {
                    System.out.println("Updates applied successfully.");
                    launcher.saveConfig();
                }
            }
        });

        launcher.start();
        launcher.launchApp(appJar);
    }
}
