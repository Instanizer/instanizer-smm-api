// Minimal Instanizer reseller API client. No dependencies, Node.js 18+ (built-in fetch).
//
//   export INSTANIZER_API_KEY=YOUR_API_KEY
//   node instanizer.mjs
//
// This example is read-only (balance, services, and order status when INSTANIZER_ORDER_ID is set).
// To place an order, call api.add(...) as shown at the bottom of this file.

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
    const body = new URLSearchParams({ key: this.key }); // key goes in the body only, never in the query string
    for (const [name, value] of Object.entries(params)) {
      if (value !== undefined && value !== null) body.set(name, String(value));
    }
    const res = await fetch(this.url, {
      method: "POST",
      body,
      signal: AbortSignal.timeout(this.timeoutMs),
    });
    const data = await res.json();
    // The API replies HTTP 200 for errors: check the `error` field.
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
  /** Place an order. Pass idempotencyKey so a retry is not billed twice. */
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
    console.error("Set INSTANIZER_API_KEY first.");
    process.exit(1);
  }
  const api = new Instanizer(key);

  console.log("Balance:", await api.balance());
  const services = await api.services();
  console.log(`${services.length} services. First three:`);
  for (const s of services.slice(0, 3)) {
    console.log(`  #${s.service} ${s.name} - Rp ${s.rate}/1000 (min ${s.min}, max ${s.max})`);
  }

  if (process.env.INSTANIZER_ORDER_ID) {
    console.log("Order status:", await api.status(process.env.INSTANIZER_ORDER_ID));
  }

  // Place an order (deducts balance). Example:
  // const result = await api.add({
  //   service: 1, link: "https://instagram.com/username", quantity: 1000, idempotencyKey: "order-0001",
  // });
  // console.log("Order ID:", result.order);
}

// Run only when this file is executed directly (not when imported).
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => {
    console.error("Failed:", e.message);
    process.exit(1);
  });
}
