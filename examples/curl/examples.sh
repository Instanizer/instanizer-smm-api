#!/usr/bin/env bash
# Contoh cURL untuk API reseller Instanizer.
#
#   export INSTANIZER_API_KEY=API_KEY_ANDA
#   bash examples.sh
#
# Hanya aksi baca yang dijalankan. Perintah add/refill/cancel ada di bagian bawah
# sebagai komentar karena memotong atau mengembalikan saldo.
#
# Catatan: galat dibalas HTTP 200 dengan {"error": "..."}, jadi periksa isi respons.
# Selalu kirim key di body (-d), jangan di query string.

set -euo pipefail

: "${INSTANIZER_API_KEY:?Isi INSTANIZER_API_KEY terlebih dahulu}"
API_URL="${INSTANIZER_API_URL:-https://api.instanizer.com/api/v2}"

echo "# Saldo"
curl -sS -X POST "$API_URL" \
  -d "key=$INSTANIZER_API_KEY" \
  -d action=balance
echo

echo "# Daftar layanan (potongan 300 karakter pertama)"
curl -sS -X POST "$API_URL" \
  -d "key=$INSTANIZER_API_KEY" \
  -d action=services | head -c 300
echo

if [ -n "${INSTANIZER_ORDER_ID:-}" ]; then
  echo "# Status satu pesanan"
  curl -sS -X POST "$API_URL" \
    -d "key=$INSTANIZER_API_KEY" \
    -d action=status \
    -d "order=$INSTANIZER_ORDER_ID"
  echo
fi

# --- Aksi yang mengubah saldo/pesanan (hapus tanda # untuk mencoba) ---
#
# Buat pesanan (idempotency_key mencegah tagihan ganda bila diulang):
# curl -sS -X POST "$API_URL" \
#   -d "key=$INSTANIZER_API_KEY" -d action=add -d service=1 \
#   --data-urlencode "link=https://instagram.com/username" \
#   -d quantity=1000 -d idempotency_key=pesanan-0001
#
# Status beberapa pesanan (maks. 100 ID):
# curl -sS -X POST "$API_URL" -d "key=$INSTANIZER_API_KEY" -d action=status -d orders=23501,23502
#
# Refill:
# curl -sS -X POST "$API_URL" -d "key=$INSTANIZER_API_KEY" -d action=refill -d order=23501
#
# Status refill:
# curl -sS -X POST "$API_URL" -d "key=$INSTANIZER_API_KEY" -d action=refill_status -d refill=23501
#
# Batalkan:
# curl -sS -X POST "$API_URL" -d "key=$INSTANIZER_API_KEY" -d action=cancel -d orders=23501
