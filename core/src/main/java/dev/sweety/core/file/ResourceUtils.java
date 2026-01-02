package dev.sweety.core.file;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@UtilityClass
public class ResourceUtils {

    @SneakyThrows
    public String loadResource(String path) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
             Scanner scanner = new Scanner(in == null ? ResourceUtils.class.getResourceAsStream(path) : in, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").next();
        }
    }

    @SneakyThrows
    public List<String> readAllLines(String path) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
             BufferedReader br = new BufferedReader(new InputStreamReader(in == null ? ResourceUtils.class.getResourceAsStream(path) : in))) {
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    @SneakyThrows
    public void zipDirectory(Path sourceDir, Path targetZip) {
        File dir = sourceDir.toFile();
        Files.write(targetZip, ZipUtils.fromFile(dir));
    }

    @SneakyThrows
    public File unzipFile(Path zipFile, Path targetDir) {
        return ZipUtils.unzip(Files.readAllBytes(zipFile), targetDir.toFile());
    }

    public byte[] zipBytes(byte[] data, String entryName) {
        return ZipUtils.zipByteArray(data, entryName);
    }

    public byte[] unzipBytes(byte[] zipData) {
        return ZipUtils.unzipFirstFileFromZip(zipData);
    }

}
