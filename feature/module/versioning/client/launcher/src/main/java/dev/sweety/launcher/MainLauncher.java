package dev.sweety.launcher;

import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.versioning.version.LatestInfo;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class MainLauncher {

    public static void main(String[] args) throws Exception {
        Path configFile = Path.of("config.json");
        Path appJar = Path.of("app.jar");
        Path selfJar = Path.of("launcher.jar");
        LauncherConfig config = LauncherConfig.load(configFile);
        AtomicReference<LauncherConfig> configRef = new AtomicReference<>(config);

        NotificationListener notificationListener = new NotificationListener(
                config.websocketUrl(),
                config.clientId(),
                config.localLauncherVersion(),
                config.localAppVersion(),
                (launcherVer, appVer) -> {
                    try {
                        LauncherConfig current = configRef.get();
                        System.out.println("Received update notification: launcher=" + launcherVer + ", app=" + appVer);
                        if (launcherVer != null && !launcherVer.equals(current.launcher())) {
                            System.out.println("Launcher update available, scheduling self-update");
                            Updater.updateLauncherSelf(current.serverUrl(), current.clientId(), launcherVer, selfJar);
                        }
                        if (appVer != null && !appVer.equals(current.localAppVersion())) {
                            System.out.println("App update available, downloading...");
                            Updater.updateApp(current.serverUrl(), current.clientId(), appVer, appJar);
                            LauncherConfig updated = current.withVersions(current.launcher(), appVer);
                            configRef.set(updated);
                            LauncherConfig.save(configFile, updated);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to handle notification: " + e.getMessage());
                    }
                }
        );
        new Thread(notificationListener::startListening, "launcher-ws").start();
        Runtime.getRuntime().addShutdownHook(new Thread(notificationListener::stopListening, "launcher-ws-shutdown"));

        LatestInfo latest;
        try {
            latest = Updater.fetchLatest(config.serverUrl());
        } catch (Exception e) {
            System.err.println("Update check non disponibile, avvio app locale: " + e.getMessage());
            launchApp(appJar).waitFor();
            return;
        }

        boolean launcherUpdate = Updater.needsLauncherUpdate(config, latest);
        boolean appUpdate = Updater.needsAppUpdate(config, latest);

        if (appUpdate) {
            Updater.updateApp(config.serverUrl(), config.clientId().toString(), latest.app().toString(), appJar);
            System.out.println("App aggiornata a " + latest.app());
            config = config.withVersions(config.launcher(), latest.app());
            configRef.set(config);
            LauncherConfig.save(configFile, config);
        }

        if (launcherUpdate) {
            boolean relaunchScheduled = Updater.updateLauncherSelf(config.serverUrl(), config.clientId().toString(), latest.launcher().toString(), selfJar);
            config = config.withVersions(latest.launcher(), config.app());
            configRef.set(config);
            LauncherConfig.save(configFile, config);
            if (relaunchScheduled) {
                System.out.println("Launcher aggiornato, riavvio avviato.");
                return;
            }
        }

        launchApp(appJar).waitFor();

        while (true) {
            Thread.onSpinWait();
        }
    }

    private static Process launchApp(Path appJar) throws Exception {
        return new ProcessBuilder("java", "-jar", appJar.toString())
                .inheritIO()
                .start();
    }
}