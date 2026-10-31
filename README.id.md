[English](README.md) | Bahasa Indonesia

# Instanizer SMM API

Spesifikasi OpenAPI dan contoh kode 10+ bahasa untuk **API reseller Instanizer**, SMM panel
Indonesia. API ini mengikuti konvensi "SMM panel API v2" sehingga skrip reseller yang sudah
ada biasanya cukup mengganti URL dan API key.

> OpenAPI spec and code examples in 10+ languages for the [Instanizer](https://instanizer.com)
> SMM panel reseller API (v2-compatible): services, add order, status, refill, cancel, balance.

- Dokumentasi lengkap: https://instanizer.com/api-docs
- Spesifikasi: [`openapi.yaml`](openapi.yaml) (OpenAPI 3.0.3)
- Endpoint: `POST https://api.instanizer.com/api/v2`
- Harga dan katalog: https://instanizer.com/layanan · data harga terbuka: https://instanizer.com/indeks-harga-smm

## Mulai cepat

1. Daftar dan verifikasi email di [instanizer.com](https://instanizer.com), lalu buat API key di
   halaman [Akses API](https://instanizer.com/api-docs).
2. Isi saldo, lalu panggil API:

```bash
curl -X POST https://api.instanizer.com/api/v2 \
  -d key=API_KEY_ANDA \
  -d action=balance
# {"balance":"250000.00","currency":"IDR"}
```

## Aksi yang tersedia

Semua aksi dikirim ke satu endpoint lewat parameter `action`.

| `action` | Fungsi | Parameter utama |
|---|---|---|
| `services` | Daftar layanan dan harga per 1.000 (Rupiah) | |
| `add` | Buat pesanan | `service`, `link`, `quantity`, `idempotency_key` (opsional) |
| `status` | Status satu atau banyak pesanan | `order` atau `orders` (maks. 100, dipisah koma) |
| `refill` | Minta refill | `order` atau `orders` |
| `refill_status` | Status refill | `refill` atau `refills` |
| `cancel` | Batalkan pesanan | `orders` (maks. 100) |
| `balance` | Saldo akun | |

## Aturan penting

- **Galat dibalas HTTP 200** dengan body `{"error": "..."}`. Selalu periksa field `error`, jangan
  hanya kode status HTTP.
- **Kirim `key` di body**, bukan di query string. Parameter di query string diabaikan agar key
  tidak masuk log akses.
- **Gunakan `idempotency_key` pada `add`.** Jika koneksi terputus sebelum Anda menerima respons,
  kirim ulang dengan kunci yang sama dan pesanan tidak dibuat dua kali.
- **Batas laju:** 300 permintaan/menit per key, 60 pesanan/menit per key, 30 kegagalan
  autentikasi per 15 menit per IP.
- **Status diperbarui sekitar 10 menit sekali.** Tidak perlu memeriksa lebih sering.
- Ambil ulang `services` secara berkala (misalnya sekali sehari): layanan bisa ditambah atau
  dihapus dari katalog.
- Jangan menaruh API key di repo, kode klien sisi browser, atau tangkapan layar.

## Contoh kode

Setiap contoh adalah satu klien kecil dengan metode untuk semua aksi. Demo-nya **hanya membaca**
(saldo, daftar layanan, dan status bila `INSTANIZER_ORDER_ID` diisi); membuat pesanan ada sebagai
komentar karena memotong saldo.

```bash
export INSTANIZER_API_KEY=API_KEY_ANDA
```

| Bahasa | Berkas | Jalankan | Dependensi |
|---|---|---|---|
| cURL | [`examples/curl/examples.sh`](examples/curl/examples.sh) | `bash examples.sh` | curl |
| Python 3.8+ | [`examples/python/instanizer.py`](examples/python/instanizer.py) | `python instanizer.py` | tidak ada |
| Node.js 18+ | [`examples/nodejs/instanizer.mjs`](examples/nodejs/instanizer.mjs) | `node instanizer.mjs` | tidak ada |
| TypeScript | [`examples/typescript/instanizer.ts`](examples/typescript/instanizer.ts) | `npx tsx instanizer.ts` | tidak ada (tsx via npx) |
| PHP 7.4+ | [`examples/php/instanizer.php`](examples/php/instanizer.php) | `php instanizer.php` | ekstensi curl, json |
| Java 17+ | [`examples/java/Instanizer.java`](examples/java/Instanizer.java) | `java Instanizer.java` | tidak ada |
| C# (.NET 8) | [`examples/csharp/`](examples/csharp) | `dotnet run` | tidak ada |
| Go 1.20+ | [`examples/go/main.go`](examples/go/main.go) | `go run .` | tidak ada |
| Ruby 3.0+ | [`examples/ruby/instanizer.rb`](examples/ruby/instanizer.rb) | `ruby instanizer.rb` | tidak ada |
| Rust | [`examples/rust/`](examples/rust) | `cargo run` | `ureq`, `serde_json` |
| Kotlin (JVM) | [`examples/kotlin/Instanizer.kt`](examples/kotlin/Instanizer.kt) | `kotlinc Instanizer.kt -include-runtime -d instanizer.jar && java -jar instanizer.jar` | tidak ada |

Status pengujian contoh: cURL, Python, Node.js, TypeScript, dan Rust sudah dijalankan terhadap
server tiruan yang meniru kontrak API. Contoh PHP, Java, C#, Go, Ruby, dan Kotlin ditulis dan
ditinjau, tetapi belum dikompilasi atau dijalankan secara otomatis. Jika Anda menemukan masalah,
buka *issue*.

Untuk mencoba tanpa API key sungguhan, arahkan klien ke server lain dengan
`INSTANIZER_API_URL`, misalnya server tiruan buatan sendiri.

## Contoh: membuat pesanan

```python
from instanizer import Instanizer

api = Instanizer("API_KEY_ANDA")
result = api.add(
    service=1,
    link="https://instagram.com/username",
    quantity=1000,
    idempotency_key="pesanan-0001",   # aman diulang bila koneksi putus
)
print("Order ID:", result["order"])
print(api.status(result["order"]))
```

## Menggunakan spesifikasi OpenAPI

[`openapi.yaml`](openapi.yaml) bisa dimuat ke Swagger UI, Postman, atau generator klien seperti
OpenAPI Generator. Karena semua aksi memakai satu endpoint, generator akan menghasilkan satu
operasi dengan berbagai bentuk permintaan (dibedakan oleh `action`); contoh klien di atas
lebih ringkas untuk pemakaian sehari-hari.

## Lisensi

MIT. Lihat [LICENSE](LICENSE). Layanan Instanizer tunduk pada
[syarat dan ketentuan](https://instanizer.com/terms) situs.
