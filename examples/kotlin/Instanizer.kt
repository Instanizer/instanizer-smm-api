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
 * Minimal Instanizer reseller API client. Kotlin (JVM) with Java 17+, no dependencies.
 *
 *   export INSTANIZER_API_KEY=YOUR_API_KEY
 *   kotlinc Instanizer.kt -include-runtime -d instanizer.jar
 *   java -jar instanizer.jar
 *
 * The methods return raw JSON (String). Use kotlinx.serialization, Moshi,
 * or Jackson to parse it in your application.
 *
 * This example is read-only (balance, services, and order status when INSTANIZER_ORDER_ID is set).
 */
class Instanizer(
    private val key: String,
    private val url: String = System.getenv("INSTANIZER_API_URL") ?: "https://api.instanizer.com/api/v2",
) {
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

    /** Send an action to the API and return the raw JSON. Throws IOException if the API replies {"error": ...}. */
    fun call(params: Map<String, String>): String {
        // key goes in the body only, never in the query string
        val form = (params + ("key" to key)).entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val body = http.send(request, HttpResponse.BodyHandlers.ofString()).body()

        // The API replies HTTP 200 for errors: check for an {"error": "..."} object.
        if (body.trimStart().startsWith("{\"error\"")) throw IOException("API error: $body")
        return body
    }

    private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

    fun services(): String = call(mapOf("action" to "services"))

    fun balance(): String = call(mapOf("action" to "balance"))

    /** Place an order. Pass idempotencyKey so a retry is not billed twice. */
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

    /** orders: comma-separated IDs, at most 100, e.g. "23501,23502". */
    fun multiStatus(orders: String): String = call(mapOf("action" to "status", "orders" to orders))

    fun refill(order: String): String = call(mapOf("action" to "refill", "order" to order))

    fun refillStatus(refill: String): String = call(mapOf("action" to "refill_status", "refill" to refill))

    fun cancel(orders: String): String = call(mapOf("action" to "cancel", "orders" to orders))
}

fun main() {
    val key = System.getenv("INSTANIZER_API_KEY")
    if (key.isNullOrBlank()) {
        System.err.println("Set INSTANIZER_API_KEY first.")
        exitProcess(1)
    }
    val api = Instanizer(key)

    try {
        println("Balance: ${api.balance()}")
        val services = api.services()
        println("Services (excerpt): ${services.take(300)}...")

        val orderId = System.getenv("INSTANIZER_ORDER_ID")
        if (!orderId.isNullOrBlank()) println("Order status: ${api.status(orderId)}")

        // Place an order (deducts balance). Example:
        // println(api.add(1, "https://instagram.com/username", 1000, "order-0001")) // {"order":23501}
    } catch (e: IOException) {
        System.err.println("Failed: ${e.message}")
        exitProcess(1)
    }
}
