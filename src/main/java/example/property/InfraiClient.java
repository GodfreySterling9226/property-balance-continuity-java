package example.property;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiClient {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final URI baseUrl;
    private final String apiKey;
    private final HttpClient http;

    public InfraiClient(ServiceConfig config) {
        this(config.baseUrl(), config.apiKey(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiClient(URI baseUrl, String apiKey, HttpClient http) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.http = http;
    }

    public void configureAutoRecharge(int triggerBalance, int rechargeAmount) {
        String body = "{\"trigger_balance\":" + triggerBalance
                + ",\"recharge_amount\":" + rechargeAmount + "}";
        request("PUT", "/v1/account/autorecharge/configure", body, true);
    }

    public String sendEmail(String to, String subject, String text) {
        String body = "{\"to\":\"" + escape(to) + "\",\"subject\":\"" + escape(subject)
                + "\",\"body\":\"" + escape(text) + "\"}";
        return extract(request("POST", "/v1/email/send", body, true), MESSAGE_ID, "message_id");
    }

    public String createTemporaryKey(String projectId, String name, String idempotencyKey) {
        String body = "{\"project_id\":\"" + escape(projectId) + "\",\"name\":\""
                + escape(name) + "\",\"scopes\":[\"account.keys.create\",\"account.keys.rotate\","
                + "\"account.keys.revoke\"],\"idempotency_key\":\"" + escape(idempotencyKey) + "\"}";
        return request("POST", "/v1/account/keys/create", body, true);
    }

    public void rotateTemporaryKey(String id, int graceHours, String idempotencyKey) {
        String body = "{\"grace_hours\":" + graceHours + ",\"idempotency_key\":\""
                + escape(idempotencyKey) + "\"}";
        request("POST", "/v1/account/keys/rotate/" + pathSegment(id), body, true);
    }

    public void revokeTemporaryKey(String id) {
        request("DELETE", "/v1/account/keys/revoke/" + pathSegment(id), null, true);
    }

    private String request(String method, String path, String body, boolean retryable) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUrl.resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json");
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            try {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Envelope envelope = decodeEnvelope(response.body(), response.statusCode());
                if (response.statusCode() == 429 && retryable && attempt < 3) {
                    sleep(retryDelayMillis(response, attempt));
                    continue;
                }
                if (!envelope.ok()) {
                    throw new InfraiException(envelope.code(), envelope.message(), response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new IOException("Infrai transport status " + response.statusCode());
                }
                return response.body();
            } catch (IOException e) {
                throw new IllegalStateException("Infrai request could not be completed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Infrai request interrupted", e);
            }
        }
        throw new IllegalStateException("retry attempts exhausted");
    }

    private static Envelope decodeEnvelope(String json, int status) throws IOException {
        Matcher ok = OK.matcher(json);
        if (!ok.find()) {
            throw new IOException("Response is not an Infrai envelope (HTTP " + status + ")");
        }
        if (Boolean.parseBoolean(ok.group(1))) {
            return new Envelope(true, "", "");
        }
        return new Envelope(false, optional(json, ERROR_CODE, "REQUEST_REJECTED"),
                optional(json, ERROR_MESSAGE, "Infrai rejected the request"));
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Math.max(1L, Long.parseLong(value)) * 1_000L;
                    } catch (NumberFormatException ignored) {
                        return 500L << attempt;
                    }
                }).orElse(500L << attempt);
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static String extract(String json, Pattern pattern, String field) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Response data omitted " + field);
        }
        return matcher.group(1);
    }

    private static String optional(String json, Pattern pattern, String fallback) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String pathSegment(String value) {
        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("invalid key id");
        }
        return value;
    }

    private record Envelope(boolean ok, String code, String message) {}
}
