package dev.sweety.versioning.server.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;

public class RollbackClientCli {

    public static void main(String[] args) throws IOException, InterruptedException {
        String serverUrl = "http://localhost:8080/rollback";
        String token = "token";

        String artifact = getArg(args, 0, "Artifact", "APP");
        String channel = getArg(args, 1, "Channel", "stable");
        String stepsStr = getArg(args, 2, "Steps", null);

        int steps;
        if (stepsStr == null) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Insert Steps (Number of rollback): ");
            stepsStr = scanner.nextLine();
            while (stepsStr.isBlank()) {
                System.out.print("Steps is required: ");
                stepsStr = scanner.nextLine();
            }
        }
        steps = Integer.parseInt(stepsStr);

        String boundary = UUID.randomUUID().toString();
        byte[] body = createMultipartBody(boundary, artifact, channel);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            for (int i = 0; i < steps; i++) {
                System.out.printf("Rollback step %d/%d... ", i + 1, steps);
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("Status: " + response.statusCode());
                System.out.println("Response: " + response.body());
            }
        }
    }

    private static String getArg(String[] args, int index, String label, String defaultValue) {
        if (args.length > index) return args[index];
        return defaultValue;
    }

    private static byte[] createMultipartBody(String boundary, String artifact, String channel) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8), true)) {
            addFormField(writer, boundary, "artifact", artifact);
            addFormField(writer, boundary, "channel", channel);
            writer.append("--").append(boundary).append("--\r\n");
        }
        return baos.toByteArray();
    }

    private static void addFormField(PrintWriter writer, String boundary, String name, String value) {
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        writer.append(value).append("\r\n");
    }
}
