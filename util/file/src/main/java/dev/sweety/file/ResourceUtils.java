package dev.sweety.file;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ResourceUtils {

    public static String loadResource(String path) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
             Scanner scanner = new Scanner(in == null ? ResourceUtils.class.getResourceAsStream(path) : in, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").next();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load resource: " + path, e);
        }
    }

    public static List<String> readAllLines(String path) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
             BufferedReader br = new BufferedReader(new InputStreamReader(in == null ? ResourceUtils.class.getResourceAsStream(path) : in))) {
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read resource lines: " + path, e);
        }
        return lines;
    }

    public static void zipDirectory(Path sourceDir, Path targetZip) {
        File dir = sourceDir.toFile();
        try {
            Files.write(targetZip, ArchiveUtils.zipSmart(dir));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to zip directory: " + sourceDir, e);
        }
    }

    public static File unzipFile(Path zipFile, Path targetDir) {
        try {
            return ArchiveUtils.unzip(Files.readAllBytes(zipFile), targetDir.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to unzip file: " + zipFile, e);
        }
    }

}
