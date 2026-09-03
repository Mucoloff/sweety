package dev.sweety.versioning.server.cli;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Scanner;
import java.util.UUID;

public class UpdateClientCli {

    public static void main(String[] args) throws Exception {
        String serverUrl = "http://localhost:8080/webhook";
        String secret = "secret";

        String artifact = getArg(args, 0, "Artifact", "APP");
        String channel = getArg(args, 1, "Channel", "stable");
        String appVersion = getArg(args, 2, "Version", null);
        String pathStr = getArg(args, 3, "Jar Path", null);

        if (appVersion == null || pathStr == null) {
             Scanner scanner = new Scanner(System.in);
             if (appVersion == null) {
                 System.out.print("Insert Version: ");
                 appVersion = scanner.nextLine();
             }
             if (pathStr == null) {
                 System.out.print("Insert Jar Path: ");
                 pathStr = scanner.nextLine();
             }
        }

        Path jarPath = Path.of(pathStr);
        if (!Files.exists(jarPath)) {
            System.err.println("File not found: " + jarPath.toAbsolutePath());
            return;
        }

        String boundary = UUID.randomUUID().toString();
        byte[] body = createMultipartBody(boundary, artifact, channel, appVersion, jarPath);

        String signature = sign(body, secret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Server response: " + response.statusCode());
            System.out.println(response.body());
        }
    }

    private static String getArg(String[] args, int index, String label, String defaultValue) {
        if (args.length > index) return args[index];
        return defaultValue;
    }

    private static byte[] createMultipartBody(String boundary, String artifact, String channel, String version, Path jarPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8), true)) {
            addFormField(writer, boundary, "artifact", artifact);
            addFormField(writer, boundary, "channel", channel);
            addFormField(writer, boundary, "version", version);
            
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"jar\"; filename=\"app.jar\"\r\n");
            writer.append("Content-Type: application/java-archive\r\n\r\n");
            writer.flush();
            Files.copy(jarPath, baos);
            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
        }
        return baos.toByteArray();
    }

    private static void addFormField(PrintWriter writer, String boundary, String name, String value) {
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        writer.append(value).append("\r\n");
    }

    private static String sign(byte[] data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data));
    }
}
