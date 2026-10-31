// Minimal typed Instanizer reseller API client. No dependencies, Node.js 18+ (built-in fetch).
//
//   export INSTANIZER_API_KEY=YOUR_API_KEY
//   npx tsx instanizer.ts
//
// This example is read-only (balance, services, and order status when INSTANIZER_ORDER_ID is set).
// To place an order, call api.add(...) as shown at the bottom of this file.

const API_URL = process.env.INSTANIZER_API_URL ?? "https://api.instanizer.com/api/v2";

export interface Service {
  service: number;
  name: string;
  type: string;
  category: string;
  rate: string; // Rupiah per 1,000
  min: string;
  max: string;
  refill: boolean;
  cancel: boolean;
}

export type OrderStatusName = "Pending" | "Processing" | "In progress" | "Completed" | "Partial" | "Canceled";

export interface OrderStatus {
  charge: string;
  start_count: string;
  status: OrderStatusName;
  remains: string;
  currency: "IDR";
}

export interface AddOrderInput {
  service: number;
  link?: string;
  quantity?: number;
  idempotencyKey?: string;
  [extra: string]: string | number | undefined;
}

export class InstanizerError extends Error {}

type Params = Record<string, string | number | undefined | null>;

export class Instanizer {
  constructor(
    private readonly key: string,
    private readonly url: string = API_URL,
    private readonly timeoutMs: number = 60_000,
  ) {}

  private async call<T>(params: Params): Promise<T> {
    const body = new URLSearchParams({ key: this.key }); // key goes in the body only, never in the query string
    for (const [name, value] of Object.entries(params)) {
      if (value !== undefined && value !== null) body.set(name, String(value));
    }
    const res = await fetch(this.url, { method: "POST", body, signal: AbortSignal.timeout(this.timeoutMs) });
    const data: unknown = await res.json();
    // The API replies HTTP 200 for errors: check the `error` field.
    if (data && !Array.isArray(data) && typeof (data as { error?: unknown }).error === "string") {
      throw new InstanizerError((data as { error: string }).error);
    }
    return data as T;
  }

  services(): Promise<Service[]> {
    return this.call({ action: "services" });
  }
  balance(): Promise<{ balance: string; currency: "IDR" }> {
    return this.call({ action: "balance" });
  }
  /** Place an order. Pass idempotencyKey so a retry is not billed twice. */
  add({ idempotencyKey, ...rest }: AddOrderInput): Promise<{ order: number }> {
    return this.call({ action: "add", idempotency_key: idempotencyKey, ...rest });
  }
  status(order: number | string): Promise<OrderStatus> {
    return this.call({ action: "status", order });
  }
  multiStatus(orders: Array<number | string>): Promise<Record<string, OrderStatus | { error: string }>> {
    return this.call({ action: "status", orders: orders.join(",") });
  }
  refill(order: number | string): Promise<{ refill: number }> {
    return this.call({ action: "refill", order });
  }
  refillStatus(refill: number | string): Promise<{ status: string }> {
    return this.call({ action: "refill_status", refill });
  }
  cancel(orders: Array<number | string>): Promise<Array<{ order: number | string; cancel: 1 | { error: string } }>> {
    return this.call({ action: "cancel", orders: orders.join(",") });
  }
}

async function main(): Promise<void> {
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

main().catch((e: Error) => {
  console.error("Failed:", e.message);
  process.exit(1);
});
