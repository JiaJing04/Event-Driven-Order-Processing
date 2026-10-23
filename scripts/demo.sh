#!/usr/bin/env bash
# Exercises the three saga outcomes against a running stack (docker compose --profile apps up --build).
# Requires: curl. Output is plain JSON; if `jq` is installed it is pretty-printed.
set -euo pipefail

ORDER_URL="${ORDER_URL:-http://localhost:8081}"
INVENTORY_URL="${INVENTORY_URL:-http://localhost:8082}"
PAYMENT_URL="${PAYMENT_URL:-http://localhost:8083}"

pretty() { if command -v jq >/dev/null 2>&1; then jq .; else cat; echo; fi; }

order_id_of() { sed -n 's/.*"id":"\([0-9a-f-]\{36\}\)".*/\1/p' | head -n1; }

status_of() { sed -n 's/.*"status":"\([A-Z]*\)".*/\1/p' | head -n1; }

place_order() { # customer sku qty price idempotency-key
  curl -sS -X POST "$ORDER_URL/api/orders" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $5" \
    -d "{\"customerId\":\"$1\",\"items\":[{\"sku\":\"$2\",\"quantity\":$3,\"unitPrice\":$4}]}"
}

wait_for_final_state() { # order-id
  local status=""
  for _ in $(seq 1 40); do
    status="$(curl -sS "$ORDER_URL/api/orders/$1" | status_of)"
    if [ "$status" = "CONFIRMED" ] || [ "$status" = "CANCELLED" ]; then break; fi
    sleep 0.5
  done
  curl -sS "$ORDER_URL/api/orders/$1" | pretty
}

scenario() { echo; echo "=================== $1 ==================="; }

scenario "1. Happy path: 2 laptops for alice -> CONFIRMED"
KEY="demo-$(date +%s)-1"
RESPONSE="$(place_order alice SKU-LAPTOP 2 499.00 "$KEY")"
echo "$RESPONSE" | pretty
ID="$(echo "$RESPONSE" | order_id_of)"
wait_for_final_state "$ID"
echo "stock after:"; curl -sS "$INVENTORY_URL/api/stock/SKU-LAPTOP" | pretty

scenario "2. Idempotency: replaying the SAME request returns the SAME order (HTTP 200, no second order)"
curl -sS -i -X POST "$ORDER_URL/api/orders" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d '{"customerId":"alice","items":[{"sku":"SKU-LAPTOP","quantity":2,"unitPrice":499.00}]}' \
  | grep -Ei '^(HTTP/|Idempotent-Replay)' || true
echo "stock unchanged:"; curl -sS "$INVENTORY_URL/api/stock/SKU-LAPTOP" | pretty

scenario "3. Not enough stock: 5 x SKU-RARE (only 1 exists) -> CANCELLED"
ID="$(place_order bob SKU-RARE 5 10.00 "demo-$(date +%s)-3" | order_id_of)"
wait_for_final_state "$ID"

scenario "4. Payment declined: customer name contains DECLINE -> stock reserved, then released -> CANCELLED"
echo "stock before:"; curl -sS "$INVENTORY_URL/api/stock/SKU-PHONE" | pretty
ID="$(place_order carol-DECLINE SKU-PHONE 3 100.00 "demo-$(date +%s)-4" | order_id_of)"
wait_for_final_state "$ID"
sleep 1
echo "stock after compensation (must equal 'before'):"; curl -sS "$INVENTORY_URL/api/stock/SKU-PHONE" | pretty
echo "reservation:"; curl -sS "$INVENTORY_URL/api/reservations/$ID" | pretty
echo "payment:"; curl -sS "$PAYMENT_URL/api/payments/by-order/$ID" | pretty
