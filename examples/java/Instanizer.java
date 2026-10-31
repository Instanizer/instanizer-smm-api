import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal Instanizer reseller API client. Java 17+, no dependencies.
 *
 * <pre>
 *   export INSTANIZER_API_KEY=YOUR_API_KEY
 *   java Instanizer.java
 * </pre>
 *
 * Java has no built-in JSON parser, so the methods return raw JSON (String).
 * Use Jackson, Gson, or org.json to parse it in your application.
 *
 * This example is read-only (balance, services, and order status when INSTANIZER_ORDER_ID is set).
 */
public class Instanizer {
    private static final String DEFAULT_URL = "https://api.instanizer.com/api/v2";

    private final String key;
    private final String url;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public Instanizer(String key) {
        this(key, System.getenv().getOrDefault("INSTANIZER_API_URL", DEFAULT_URL));
    }

    public Instanizer(String key, String url) {
        this.key = key;
        this.url = url;
    }

    /** Send an action to the API and return the raw JSON. Throws IOException if the API replies {"error": ...}. */
    public String call(Map<String, String> params) throws IOException, InterruptedException {
        Map<String, String> all = new LinkedHashMap<>(params);
        all.put("key", key); // key goes in the body only, never in the query string
        String form = all.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        String body = http.send(request, HttpResponse.BodyHandlers.ofString()).body();

        // The API replies HTTP 200 for errors: check for an {"error": "..."} object.
        if (body.stripLeading().startsWith("{\"error\"")) {
            throw new IOException("API error: " + body);
        }
        return body;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public String services() throws IOException, InterruptedException {
        return call(Map.of("action", "services"));
    }

    public String balance() throws IOException, InterruptedException {
        return call(Map.of("action", "balance"));
    }

    /** Place an order. Pass idempotencyKey so a retry is not billed twice. */
    public String add(int service, String link, int quantity, String idempotencyKey)
            throws IOException, InterruptedException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("action", "add");
        p.put("service", String.valueOf(service));
        p.put("link", link);
        p.put("quantity", String.valueOf(quantity));
        if (idempotencyKey != null) p.put("idempotency_key", idempotencyKey);
        return call(p);
    }

    public String status(String order) throws IOException, InterruptedException {
        return call(Map.of("action", "status", "order", order));
    }

    public String multiStatus(String orders) throws IOException, InterruptedException {
        return call(Map.of("action", "status", "orders", orders)); // "23501,23502"
    }

    public String refill(String order) throws IOException, InterruptedException {
        return call(Map.of("action", "refill", "order", order));
    }

    public String refillStatus(String refill) throws IOException, InterruptedException {
        return call(Map.of("action", "refill_status", "refill", refill));
    }

    public String cancel(String orders) throws IOException, InterruptedException {
        return call(Map.of("action", "cancel", "orders", orders));
    }

    public static void main(String[] args) throws Exception {
        String key = System.getenv("INSTANIZER_API_KEY");
        if (key == null || key.isBlank()) {
            System.err.println("Set INSTANIZER_API_KEY first.");
            System.exit(1);
        }
        Instanizer api = new Instanizer(key);

        System.out.println("Balance: " + api.balance());
        String services = api.services();
        System.out.println("Services (excerpt): " + services.substring(0, Math.min(300, services.length())) + "...");

        String orderId = System.getenv("INSTANIZER_ORDER_ID");
        if (orderId != null && !orderId.isBlank()) {
            System.out.println("Order status: " + api.status(orderId));
        }

        // Place an order (deducts balance). Example:
        // String result = api.add(1, "https://instagram.com/username", 1000, "order-0001");
        // System.out.println(result); // {"order":23501}
    }
}
