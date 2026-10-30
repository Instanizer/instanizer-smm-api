"""Klien kecil API reseller Instanizer. Tanpa dependensi, Python 3.8+.

    export INSTANIZER_API_KEY=API_KEY_ANDA
    python instanizer.py

Contoh ini hanya membaca (saldo, layanan, dan status bila INSTANIZER_ORDER_ID diisi).
Untuk membuat pesanan, panggil api.add(...) seperti contoh di bagian bawah berkas.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

API_URL = os.environ.get("INSTANIZER_API_URL", "https://api.instanizer.com/api/v2")


class InstanizerError(Exception):
    """Galat dari API. API membalas HTTP 200 dengan {"error": "..."}."""


class Instanizer:
    def __init__(self, key, url=API_URL, timeout=60):
        self.key = key
        self.url = url
        self.timeout = timeout

    def _call(self, **params):
        params = {k: v for k, v in params.items() if v is not None}
        params["key"] = self.key  # key hanya di body, bukan di query string
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
        """Buat pesanan. Kirim idempotency_key agar pengulangan tidak menagih dua kali."""
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
        sys.exit("Isi INSTANIZER_API_KEY terlebih dahulu.")
    api = Instanizer(key)

    print("Saldo:", api.balance())
    services = api.services()
    print(f"{len(services)} layanan. Tiga pertama:")
    for s in services[:3]:
        print(f"  #{s['service']} {s['name']} - Rp {s['rate']}/1000 (min {s['min']}, max {s['max']})")

    order_id = os.environ.get("INSTANIZER_ORDER_ID")
    if order_id:
        print("Status pesanan:", api.status(order_id))

    # Membuat pesanan (memotong saldo). Contoh:
    # result = api.add(service=1, link="https://instagram.com/username",
    #                  quantity=1000, idempotency_key="pesanan-0001")
    # print("Order ID:", result["order"])


if __name__ == "__main__":
    try:
        main()
    except InstanizerError as e:
        sys.exit(f"Gagal: {e}")
