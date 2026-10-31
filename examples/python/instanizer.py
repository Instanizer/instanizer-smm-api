"""Minimal Instanizer reseller API client. No dependencies, Python 3.8+.

    export INSTANIZER_API_KEY=YOUR_API_KEY
    python instanizer.py

This example is read-only (balance, services, and order status when INSTANIZER_ORDER_ID is set).
To place an order, call api.add(...) as shown at the bottom of this file.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

API_URL = os.environ.get("INSTANIZER_API_URL", "https://api.instanizer.com/api/v2")


class InstanizerError(Exception):
    """Error returned by the API. The API replies HTTP 200 with {"error": "..."}."""


class Instanizer:
    def __init__(self, key, url=API_URL, timeout=60):
        self.key = key
        self.url = url
        self.timeout = timeout

    def _call(self, **params):
        params = {k: v for k, v in params.items() if v is not None}
        params["key"] = self.key  # key goes in the body only, never in the query string
        data = urllib.parse.urlencode(params).encode()
        req = urllib.request.Request(self.url, data=data, method="POST")
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            body = json.loads(resp.read().decode())
        if isinstance(body, dict) and "error" in body:
            raise InstanizerError(body["error"])
        return body

    def services(self):
        return self._call(action="services")

    def balance(self):
        return self._call(action="balance")

    def add(self, service, link=None, quantity=None, idempotency_key=None, **extra):
        """Place an order. Pass idempotency_key so a retry is not billed twice."""
        return self._call(
            action="add",
            service=service,
            link=link,
            quantity=quantity,
            idempotency_key=idempotency_key,
            **extra,
        )

    def status(self, order):
        return self._call(action="status", order=order)

    def multi_status(self, orders):
        return self._call(action="status", orders=",".join(str(o) for o in orders))

    def refill(self, order):
        return self._call(action="refill", order=order)

    def refill_status(self, refill):
        return self._call(action="refill_status", refill=refill)

    def cancel(self, orders):
        return self._call(action="cancel", orders=",".join(str(o) for o in orders))


def main():
    key = os.environ.get("INSTANIZER_API_KEY")
    if not key:
        sys.exit("Set INSTANIZER_API_KEY first.")
    api = Instanizer(key)

    print("Balance:", api.balance())
    services = api.services()
    print(f"{len(services)} services. First three:")
    for s in services[:3]:
        print(f"  #{s['service']} {s['name']} - Rp {s['rate']}/1000 (min {s['min']}, max {s['max']})")

    order_id = os.environ.get("INSTANIZER_ORDER_ID")
    if order_id:
        print("Order status:", api.status(order_id))

    # Place an order (deducts balance). Example:
    # result = api.add(service=1, link="https://instagram.com/username",
    #                  quantity=1000, idempotency_key="order-0001")
    # print("Order ID:", result["order"])


if __name__ == "__main__":
    try:
        main()
    except InstanizerError as e:
        sys.exit(f"Failed: {e}")
