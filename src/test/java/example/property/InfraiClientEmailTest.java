package example.property;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class InfraiClientEmailTest {
    public static void main(String[] args) throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/email/send", exchange -> {
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"ok\":true,\"message_id\":\"test-message\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            InfraiClient client = new InfraiClient(base, "test-key", HttpClient.newHttpClient());
            if (!"test-message".equals(client.sendEmail("chenhua@changba.com", "Notice", "Details"))) {
                throw new AssertionError("message ID was not returned");
            }
            if (!"{\"to\":\"chenhua@changba.com\",\"subject\":\"Notice\",\"body\":\"Details\"}"
                    .equals(payload.get())) {
                throw new AssertionError("unexpected email request body: " + payload.get());
            }
            System.out.println("PASS: email.send uses body instead of text");
        } finally {
            server.stop(0);
        }
    }
}
