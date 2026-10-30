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
 * Klien kecil API reseller Instanizer. Java 17+, tanpa dependensi.
 *
 * <pre>
 *   export INSTANIZER_API_KEY=API_KEY_ANDA
 *   java Instanizer.java
 * </pre>
 *
 * Java tidak punya parser JSON bawaan, jadi metode mengembalikan JSON mentah (String).
 * Gunakan Jackson, Gson, atau org.json untuk mengurainya di aplikasi Anda.
 *
 * Contoh ini hanya membaca (saldo, layanan, dan status bila INSTANIZER_ORDER_ID diisi).
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

    /** Kirim aksi ke API dan kembalikan JSON mentah. Melempar IOException bila API membalas {"error": ...}. */
    public String call(Map<String, String> params) throws IOException, InterruptedException {
        Map<String, String> all = new LinkedHashMap<>(params);
        all.put("key", key); // key hanya di body, bukan di query string
        String form = all.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        String body = http.send(request, HttpResponse.BodyHandlers.ofString()).body();

        // API membalas HTTP 200 untuk galat: periksa objek {"error": "..."}.
        if (body.stripLeading().startsWith("{\"error\"")) {
            throw new IOException("Galat API: " + body);
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

    /** Buat pesanan. Kirim idempotencyKey agar pengulangan tidak menagih dua kali. */
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
            System.err.println("Isi INSTANIZER_API_KEY terlebih dahulu.");
            System.exit(1);
        }
        Instanizer api = new Instanizer(key);

        System.out.println("Saldo: " + api.balance());
        String services = api.services();
        System.out.println("Layanan (potongan): " + services.substring(0, Math.min(300, services.length())) + "...");

        String orderId = System.getenv("INSTANIZER_ORDER_ID");
        if (orderId != null && !orderId.isBlank()) {
            System.out.println("Status pesanan: " + api.status(orderId));
        }

        // Membuat pesanan (memotong saldo). Contoh:
        // String result = api.add(1, "https://instagram.com/username", 1000, "pesanan-0001");
        // System.out.println(result); // {"order":23501}
    }
}
