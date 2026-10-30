// Klien kecil API reseller Instanizer. Tanpa dependensi, Node.js 18+ (fetch bawaan).
//
//   export INSTANIZER_API_KEY=API_KEY_ANDA
//   node instanizer.mjs
//
// Contoh ini hanya membaca (saldo, layanan, dan status bila INSTANIZER_ORDER_ID diisi).
// Untuk membuat pesanan, panggil api.add(...) seperti contoh di bagian bawah berkas.

import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const API_URL = process.env.INSTANIZER_API_URL ?? "https://api.instanizer.com/api/v2";

export class InstanizerError extends Error {}

export class Instanizer {
  constructor(key, { url = API_URL, timeoutMs = 60_000 } = {}) {
    this.key = key;
    this.url = url;
    this.timeoutMs = timeoutMs;
  }

  async call(params) {
    const body = new URLSearchParams({ key: this.key }); // key hanya di body, bukan di query string
    for (const [name, value] of Object.entries(params)) {
      if (value !== undefined && value !== null) body.set(name, String(value));
    }
    const res = await fetch(this.url, {
      method: "POST",
      body,
      signal: AbortSignal.timeout(this.timeoutMs),
    });
    const data = await res.json();
    // API membalas HTTP 200 untuk galat: periksa field `error`.
    if (data && !Array.isArray(data) && typeof data.error === "string") {
      throw new InstanizerError(data.error);
    }
    return data;
  }

  services() {
    return this.call({ action: "services" });
  }
  balance() {
    return this.call({ action: "balance" });
  }
  /** Buat pesanan. Kirim idempotencyKey agar pengulangan tidak menagih dua kali. */
  add({ service, link, quantity, idempotencyKey, ...extra }) {
    return this.call({ action: "add", service, link, quantity, idempotency_key: idempotencyKey, ...extra });
  }
  status(order) {
    return this.call({ action: "status", order });
  }
  multiStatus(orders) {
    return this.call({ action: "status", orders: orders.join(",") });
  }
  refill(order) {
    return this.call({ action: "refill", order });
  }
  refillStatus(refill) {
    return this.call({ action: "refill_status", refill });
  }
  cancel(orders) {
    return this.call({ action: "cancel", orders: orders.join(",") });
  }
}

async function main() {
  const key = process.env.INSTANIZER_API_KEY;
  if (!key) {
    console.error("Isi INSTANIZER_API_KEY terlebih dahulu.");
    process.exit(1);
  }
  const api = new Instanizer(key);

  console.log("Saldo:", await api.balance());
  const services = await api.services();
  console.log(`${services.length} layanan. Tiga pertama:`);
  for (const s of services.slice(0, 3)) {
    console.log(`  #${s.service} ${s.name} - Rp ${s.rate}/1000 (min ${s.min}, max ${s.max})`);
  }

  if (process.env.INSTANIZER_ORDER_ID) {
    console.log("Status pesanan:", await api.status(process.env.INSTANIZER_ORDER_ID));
  }

  // Membuat pesanan (memotong saldo). Contoh:
  // const result = await api.add({
  //   service: 1, link: "https://instagram.com/username", quantity: 1000, idempotencyKey: "pesanan-0001",
  // });
  // console.log("Order ID:", result.order);
}

// Jalankan hanya bila berkas ini dieksekusi langsung (bukan saat di-import).
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => {
    console.error("Gagal:", e.message);
    process.exit(1);
  });
}
