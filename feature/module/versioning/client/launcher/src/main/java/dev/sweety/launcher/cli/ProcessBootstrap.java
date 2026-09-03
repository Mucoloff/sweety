package dev.sweety.launcher.cli;

import java.nio.file.*;
import java.util.*;

public class ProcessBootstrap {

    public static void main(String[] args) throws Exception {
        String jarName = "launcher.jar";
        List<String> jvmArgs = new ArrayList<>();
        List<String> appArgs = new ArrayList<>();

        int i = 0;
        if (args.length > 0 && !args[0].startsWith("--")) {
            jarName = args[0];
            i = 1;
        }

        boolean parsingJvm = false;
        boolean parsingApp = false;

        for (; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--jar" -> {
                    if (i + 1 < args.length) {
                        jarName = args[++i];
                    }
                }
                case "--jvm" -> {
                    parsingJvm = true;
                    parsingApp = false;
                }
                case "--" -> {
                    parsingJvm = false;
                    parsingApp = true;
                }
                default -> {
                    if (parsingJvm) {
                        jvmArgs.add(arg);
                    } else if (parsingApp) {
                        appArgs.add(arg);
                    }
                }
            }
        }

        Path appJar = Path.of(jarName);
        Path newJar = Path.of(jarName + ".new");

        if (Files.exists(newJar)) {
            System.out.println("Updating to new version...");
            Files.move(newJar, appJar,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }

        List<String> command = new ArrayList<>();
        Path javaBin = Paths.get(System.getProperty("java.home"), "bin", "java");

        command.add(javaBin.toString());
        command.addAll(jvmArgs);
        command.add("-jar");
        command.add(appJar.getFileName().toString());
        command.addAll(appArgs);

        System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
    }
}
