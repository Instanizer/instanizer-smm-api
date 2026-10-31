#!/usr/bin/env bash
# cURL examples for the Instanizer reseller API.
#
#   export INSTANIZER_API_KEY=YOUR_API_KEY
#   bash examples.sh
#
# Only read actions are run. The add/refill/cancel commands are at the bottom
# as comments because they deduct or refund balance.
#
# Note: errors are returned as HTTP 200 with {"error": "..."}, so check the response body.
# Always send the key in the body (-d), never in the query string.

set -euo pipefail

: "${INSTANIZER_API_KEY:?Set INSTANIZER_API_KEY first}"
API_URL="${INSTANIZER_API_URL:-https://api.instanizer.com/api/v2}"

echo "# Balance"
curl -sS -X POST "$API_URL" \
  -d "key=$INSTANIZER_API_KEY" \
  -d action=balance
echo

echo "# Service list (first 300 characters)"
curl -sS -X POST "$API_URL" \
  -d "key=$INSTANIZER_API_KEY" \
  -d action=services | head -c 300
echo

if [ -n "${INSTANIZER_ORDER_ID:-}" ]; then
  echo "# Status of one order"
  curl -sS -X POST "$API_URL" \
    -d "key=$INSTANIZER_API_KEY" \
    -d action=status \
    -d "order=$INSTANIZER_ORDER_ID"
  echo
fi

# --- Actions that change balance/orders (remove the leading # to try) ---
#
# Place an order (idempotency_key prevents double billing on retry):
# curl -sS -X POST "$API_URL" \
#   -d "key=$INSTANIZER_API_KEY" -d action=add -d service=1 \
#   --data-urlencode "link=https://instagram.com/username" \
#   -d quantity=1000 -d idempotency_key=order-0001
#
# Status of several orders (max. 100 IDs):
# curl -sS -X POST "$API_URL" -d "key=$INSTANIZER_API_KEY" -d action=status -d orders=23501,23502
#
# Refill:
# curl -sS -X POST "$API_URL" -d "key=$INSTANIZER_API_KEY" -d action=refill -d order=23501
#
# Refill status:
# curl -sS -X POST "$API_URL" -d "key=$INSTANIZER_API_KEY" -d action=refill_status -d refill=23501
#
# Cancel:
# curl -sS -X POST "$API_URL" -d "key=$INSTANIZER_API_KEY" -d action=cancel -d orders=23501
