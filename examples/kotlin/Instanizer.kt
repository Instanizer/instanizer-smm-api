import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.system.exitProcess

/**
 * Klien kecil API reseller Instanizer. Kotlin (JVM) dengan Java 17+, tanpa dependensi.
 *
 *   export INSTANIZER_API_KEY=API_KEY_ANDA
 *   kotlinc Instanizer.kt -include-runtime -d instanizer.jar
 *   java -jar instanizer.jar
 *
 * Metode mengembalikan JSON mentah (String). Gunakan kotlinx.serialization, Moshi,
 * atau Jackson untuk mengurainya di aplikasi Anda.
 *
 * Contoh ini hanya membaca (saldo, layanan, dan status bila INSTANIZER_ORDER_ID diisi).
 */
class Instanizer(
    private val key: String,
    private val url: String = System.getenv("INSTANIZER_API_URL") ?: "https://api.instanizer.com/api/v2",
) {
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

    /** Kirim aksi ke API dan kembalikan JSON mentah. Melempar IOException bila API membalas {"error": ...}. */
    fun call(params: Map<String, String>): String {
        // key hanya di body, bukan di query string
        val form = (params + ("key" to key)).entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val body = http.send(request, HttpResponse.BodyHandlers.ofString()).body()

        // API membalas HTTP 200 untuk galat: periksa objek {"error": "..."}.
        if (body.trimStart().startsWith("{\"error\"")) throw IOException("Galat API: $body")
        return body
    }

    private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

    fun services(): String = call(mapOf("action" to "services"))

    fun balance(): String = call(mapOf("action" to "balance"))

    /** Buat pesanan. Kirim idempotencyKey agar pengulangan tidak menagih dua kali. */
    fun add(service: Int, link: String, quantity: Int, idempotencyKey: String? = null): String {
        val params = mutableMapOf(
            "action" to "add",
            "service" to service.toString(),
            "link" to link,
            "quantity" to quantity.toString(),
        )
        if (idempotencyKey != null) params["idempotency_key"] = idempotencyKey
        return call(params)
    }

    fun status(order: String): String = call(mapOf("action" to "status", "order" to order))

    /** orders: ID dipisah koma, maksimal 100, mis. "23501,23502". */
    fun multiStatus(orders: String): String = call(mapOf("action" to "status", "orders" to orders))

    fun refill(order: String): String = call(mapOf("action" to "refill", "order" to order))

    fun refillStatus(refill: String): String = call(mapOf("action" to "refill_status", "refill" to refill))

    fun cancel(orders: String): String = call(mapOf("action" to "cancel", "orders" to orders))
}

fun main() {
    val key = System.getenv("INSTANIZER_API_KEY")
    if (key.isNullOrBlank()) {
        System.err.println("Isi INSTANIZER_API_KEY terlebih dahulu.")
        exitProcess(1)
    }
    val api = Instanizer(key)

    try {
        println("Saldo: ${api.balance()}")
        val services = api.services()
        println("Layanan (potongan): ${services.take(300)}...")

        val orderId = System.getenv("INSTANIZER_ORDER_ID")
        if (!orderId.isNullOrBlank()) println("Status pesanan: ${api.status(orderId)}")

        // Membuat pesanan (memotong saldo). Contoh:
        // println(api.add(1, "https://instagram.com/username", 1000, "pesanan-0001")) // {"order":23501}
    } catch (e: IOException) {
        System.err.println("Gagal: ${e.message}")
        exitProcess(1)
    }
}
