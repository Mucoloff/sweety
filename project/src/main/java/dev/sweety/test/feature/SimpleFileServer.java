package dev.sweety.test.feature;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.Executors;

public class SimpleFileServer {
    private static final String FILE_PATH = "/run/media/sweety/shared/dev/projects/java/sweety/build/classes/java/main/dev/sweety/test/TestFeature.class";
    private static final int PORT = 8080;
    private static final String CONTEXT = "/TestFeature.class";

    public static void main(String[] args) throws IOException {
        Path path = Paths.get(FILE_PATH);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            System.err.println("File non trovato: " + FILE_PATH);
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext(CONTEXT, new FileHandler(path));
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();

        System.out.println("Server avviato: http://localhost:" + PORT + CONTEXT);
    }

    record FileHandler(Path file) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                if (!Files.exists(file)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                long size = Files.size(file);
                String contentType = Files.probeContentType(file);
                if (contentType == null) contentType = "application/octet-stream";

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, size);

                try (OutputStream os = exchange.getResponseBody(); InputStream is = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }

            } finally {
                exchange.close(); // chiude sempre la connessione
            }
        }
    }
}
