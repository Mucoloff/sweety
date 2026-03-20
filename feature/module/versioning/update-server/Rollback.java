import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;
import java.util.UUID;

public class Rollback {

    public static void main(String[] args) throws IOException, InterruptedException {

        String serverUrl = "http://localhost:8080/rollback";
        String token = "token";

        String artifact;
        String channel;
        int steps;

        if (args.length >= 3) {
            artifact = args[0];
            channel = args[1];
            steps = Integer.parseInt(args[2]);
        } else {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Insert Artifact (default: APP): ");
            String input = scanner.nextLine();
            artifact = input.isBlank() ? "APP" : input;

            System.out.print("Insert Channel (default: stable): ");
            input = scanner.nextLine();
            channel = input.isBlank() ? "stable" : input;

            System.out.print("Insert Steps (Number of rollback): ");
            String stepsStr = scanner.nextLine();
            while (stepsStr.isBlank()) {
                System.out.print("Steps is required: ");
                stepsStr = scanner.nextLine();
            }
            steps = Integer.parseInt(stepsStr);
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

        // chiusura
        writer.append("--").append(boundary).append("--\r\n");
        writer.close();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < steps; i++) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Status: " + response.statusCode());
            System.out.println("Response: " + response.body());
        }


    }

}
