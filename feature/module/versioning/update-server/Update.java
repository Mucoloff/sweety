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

class Update {

    public static void main(String[] args) throws Exception {
        String serverUrl = "http://localhost:8080/webhook";
        String secret = "secret";

        String artifact;
        String channel;
        String appVersion;
        Path jarPath;

        if (args.length >= 4) {
            artifact = args[0];
            channel = args[1];
            appVersion = args[2];
            jarPath = Path.of(args[3]);
        } else {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Insert Artifact (default: APP): ");
            String input = scanner.nextLine();
            artifact = input.isBlank() ? "APP" : input;

            System.out.print("Insert Channel (default: stable): ");
            input = scanner.nextLine();
            channel = input.isBlank() ? "stable" : input;

            System.out.print("Insert Version: ");
            appVersion = scanner.nextLine();
            while (appVersion.isBlank()) {
                System.out.print("Version is required: ");
                appVersion = scanner.nextLine();
            }

            System.out.print("Insert Jar Path: ");
            String pathStr = scanner.nextLine();
            while (pathStr.isBlank()) {
                System.out.print("Jar Path is required: ");
                pathStr = scanner.nextLine();
            }
            jarPath = Path.of(pathStr);
        }

        if (!Files.exists(jarPath)) {
            System.err.println("File not found: " + jarPath.toAbsolutePath());
            return;
        }


        // crea multipart body
        String boundary = UUID.randomUUID().toString();
        var body = new ByteArrayOutputStream();
        var writer = new PrintWriter(new OutputStreamWriter(body), true);


        // artifact
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"artifact\"\r\n\r\n");
        writer.append(artifact).append("\r\n");

        // channel
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"channel\"\r\n\r\n");
        writer.append(channel).append("\r\n");


        // appVersion
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"version\"\r\n\r\n");
        writer.append(appVersion).append("\r\n");

        // appJar
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"jar\"; filename=\"app.jar\"\r\n");
        writer.append("Content-Type: application/java-archive\r\n\r\n");

        writer.flush();
        Files.copy(jarPath, body);
        writer.append("\r\n");

        // chiusura
        writer.append("--").append(boundary).append("--\r\n");
        writer.close();

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(body.toByteArray());

        String signature = HexFormat.of().formatHex(digest); // esadecimale puro

        // invia richiesta
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Signature", signature) // se vuoi simulare firma semplice
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Server response: " + response.statusCode());
        System.out.println(response.body());
    }
}