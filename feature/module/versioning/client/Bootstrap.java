import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Bootstrap {

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

        File appJar = new File(jarName);
        File newJar = new File(jarName + ".new");

        // update
        if (newJar.exists()) {
            System.out.println("Updating to new version...");
            Files.move(newJar.toPath(), appJar.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }

        List<String> command = new ArrayList<>();

        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        command.add(javaBin);

        // VM args
        command.addAll(jvmArgs);

        // jar
        command.add("-jar");
        command.add(appJar.getName());

        // app args
        command.addAll(appArgs);

        System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
    }
}