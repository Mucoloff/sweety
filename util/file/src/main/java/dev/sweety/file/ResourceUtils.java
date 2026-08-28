package dev.sweety.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Function;

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
        readAllLines(path, pth ->  {
            InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            return in == null ? ResourceUtils.class.getResourceAsStream(path) : in;
        }, lines::add, false);
        return lines;
    }

    public static void readAllLines(String path, Function<String, InputStream> stream, Consumer<String> adder, boolean skipBlank) {
        try (
                InputStream in = stream.apply(path);
                BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (skipBlank && line.isBlank()) continue;
                adder.accept(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read resource lines: " + path, e);
        }
    }

    public static void zipDirectory(Path sourceDir, Path targetZip) {
        try (OutputStream os = Files.newOutputStream(targetZip)) {
            os.write(ArchiveUtils.zipSmart(sourceDir));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to zip directory: " + sourceDir, e);
        }
    }

    public static Path unzipFile(Path zipFile, Path targetDir) {
        try {
            return ArchiveUtils.unzip(Files.readAllBytes(zipFile), targetDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to unzip file: " + zipFile, e);
        }
    }

}
