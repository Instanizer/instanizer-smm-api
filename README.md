English | [Bahasa Indonesia](README.id.md)

# Instanizer SMM API

SMM panel API for resellers: an OpenAPI spec and code examples in 11 languages (cURL, Python,
Node.js, TypeScript, PHP, Java, C#, Go, Ruby, Rust, Kotlin) for the **Instanizer reseller API**,
an Indonesian SMM panel. The API follows the "SMM panel API v2" convention, so existing reseller
scripts usually only need a new URL and API key.

> OpenAPI spec and code examples in 11 languages for the [Instanizer](https://instanizer.com)
> SMM panel reseller API (v2-compatible): services, add order, status, refill, cancel, balance.

- Full documentation: https://instanizer.com/en/api-docs
- Specification: [`openapi.yaml`](openapi.yaml) (OpenAPI 3.0.3)
- Endpoint: `POST https://api.instanizer.com/api/v2`
- Prices and catalog: https://instanizer.com/en/layanan · open pricing data: https://instanizer.com/en/indeks-harga-smm

## Quick start

1. Sign up and verify your email at [instanizer.com](https://instanizer.com), then create an API key
   on the [API Access](https://instanizer.com/en/api-docs) page.
2. Top up your balance, then call the API:

```bash
curl -X POST https://api.instanizer.com/api/v2 \
  -d key=YOUR_API_KEY \
  -d action=balance
# {"balance":"250000.00","currency":"IDR"}
```

## Available actions

All actions are sent to a single endpoint through the `action` parameter.

| `action` | Purpose | Main parameters |
|---|---|---|
| `services` | List services and prices per 1,000 (Rupiah) | |
| `add` | Place an order | `service`, `link`, `quantity`, `idempotency_key` (optional) |
| `status` | Status of one or many orders | `order` or `orders` (max. 100, comma-separated) |
| `refill` | Request a refill | `order` or `orders` |
| `refill_status` | Refill status | `refill` or `refills` |
| `cancel` | Cancel orders | `orders` (max. 100) |
| `balance` | Account balance | |

## Important rules

- **Errors are returned with HTTP 200** and a body of `{"error": "..."}`. Always check the `error`
  field, not just the HTTP status code.
- **Send `key` in the request body**, not in the query string. Query string parameters are ignored
  so the key never ends up in access logs.
- **Use `idempotency_key` on `add`.** If the connection drops before you receive the response,
  resend with the same key and the order is not created twice.
- **Rate limits:** 300 requests/minute per key, 60 orders/minute per key, 30 authentication
  failures per 15 minutes per IP.
- **Statuses are refreshed roughly every 10 minutes.** There is no need to poll more often.
- Re-fetch `services` periodically (for example once a day): services can be added to or removed
  from the catalog.
- Never put your API key in a repo, browser-side client code, or screenshots.

## Code examples

Each example is a small client with a method for every action. The demo is **read-only**
(balance, service list, and order status when `INSTANIZER_ORDER_ID` is set); placing an order is
included as a comment because it deducts balance.

```bash
export INSTANIZER_API_KEY=YOUR_API_KEY
```

| Language | File | Run | Dependencies |
|---|---|---|---|
| cURL | [`examples/curl/examples.sh`](examples/curl/examples.sh) | `bash examples.sh` | curl |
| Python 3.8+ | [`examples/python/instanizer.py`](examples/python/instanizer.py) | `python instanizer.py` | none |
| Node.js 18+ | [`examples/nodejs/instanizer.mjs`](examples/nodejs/instanizer.mjs) | `node instanizer.mjs` | none |
| TypeScript | [`examples/typescript/instanizer.ts`](examples/typescript/instanizer.ts) | `npx tsx instanizer.ts` | none (tsx via npx) |
| PHP 7.4+ | [`examples/php/instanizer.php`](examples/php/instanizer.php) | `php instanizer.php` | curl, json extensions |
| Java 17+ | [`examples/java/Instanizer.java`](examples/java/Instanizer.java) | `java Instanizer.java` | none |
| C# (.NET 8) | [`examples/csharp/`](examples/csharp) | `dotnet run` | none |
| Go 1.20+ | [`examples/go/main.go`](examples/go/main.go) | `go run .` | none |
| Ruby 3.0+ | [`examples/ruby/instanizer.rb`](examples/ruby/instanizer.rb) | `ruby instanizer.rb` | none |
| Rust | [`examples/rust/`](examples/rust) | `cargo run` | `ureq`, `serde_json` |
| Kotlin (JVM) | [`examples/kotlin/Instanizer.kt`](examples/kotlin/Instanizer.kt) | `kotlinc Instanizer.kt -include-runtime -d instanizer.jar && java -jar instanizer.jar` | none |

Test status: the cURL, Python, Node.js, TypeScript, and Rust examples have been run against a
mock server that imitates the API contract. The PHP, Java, C#, Go, Ruby, and Kotlin examples are
written and reviewed, but have not been compiled or run automatically. If you find a problem,
please open an issue.

To try a client without a real API key, point it at another server with `INSTANIZER_API_URL`,
for example a mock server of your own.

## Example: placing an order

```python
from instanizer import Instanizer

api = Instanizer("YOUR_API_KEY")
result = api.add(
    service=1,
    link="https://instagram.com/username",
    quantity=1000,
    idempotency_key="order-0001",   # safe to retry if the connection drops
)
print("Order ID:", result["order"])
print(api.status(result["order"]))
```

## Using the OpenAPI spec

[`openapi.yaml`](openapi.yaml) can be loaded into Swagger UI, Postman, or client generators such as
OpenAPI Generator. Because every action uses a single endpoint, a generator will produce one
operation with several request shapes (distinguished by `action`); the example clients above are
more concise for everyday use.

## License

MIT. See [LICENSE](LICENSE). Use of the Instanizer service is subject to the site's
[terms and conditions](https://instanizer.com/terms).
