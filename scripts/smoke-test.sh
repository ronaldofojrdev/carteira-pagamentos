#!/usr/bin/env bash
# Teste ponta a ponta contra os servicos rodando via docker compose.
# Uso: docker compose up --build -d && ./scripts/smoke-test.sh
set -euo pipefail

ACCOUNTS=${ACCOUNTS_URL:-http://localhost:8081}
TRANSFERS=${TRANSFERS_URL:-http://localhost:8082}

json_field() { sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" | head -1; }

create_account() {
  curl -sf -X POST "$ACCOUNTS/accounts" -H 'Content-Type: application/json' \
    -d "{\"ownerName\":\"$1\",\"initialBalance\":$2}" | json_field id
}

balance() { curl -sf "$ACCOUNTS/accounts/$1" | json_field balance; }

wait_status() {
  local id=$1 expected=$2
  for _ in $(seq 1 30); do
    status=$(curl -sf "$TRANSFERS/transfers/$id" | json_field status)
    [ "$status" = "$expected" ] && return 0
    sleep 1
  done
  echo "FALHOU: transferencia $id ficou em $status (esperado $expected)"; exit 1
}

transfer() { # key from to amount
  curl -s -X POST "$TRANSFERS/transfers" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $1" \
    -d "{\"fromAccountId\":\"$2\",\"toAccountId\":\"$3\",\"amount\":$4}"
}

ALICE=$(create_account Alice 100.00)
BOB=$(create_account Bob 0)
echo "Alice=$ALICE Bob=$BOB"

echo "1) transferencia feliz de 40.00"
ID=$(transfer "smoke-$RANDOM-1" "$ALICE" "$BOB" 40.00 | json_field id)
wait_status "$ID" COMPLETED
echo "   Alice=$(balance "$ALICE") Bob=$(balance "$BOB")"

echo "2) mesma Idempotency-Key duas vezes: so deve debitar uma vez"
KEY="smoke-$RANDOM-2"
ID1=$(transfer "$KEY" "$ALICE" "$BOB" 10.00 | json_field id)
ID2=$(transfer "$KEY" "$ALICE" "$BOB" 10.00 | json_field id)
[ "$ID1" = "$ID2" ] || { echo "FALHOU: ids diferentes"; exit 1; }
wait_status "$ID1" COMPLETED
echo "   Alice=$(balance "$ALICE") Bob=$(balance "$BOB")"

echo "3) saldo insuficiente: deve terminar em FAILED"
ID=$(transfer "smoke-$RANDOM-3" "$ALICE" "$BOB" 9999.00 | json_field id)
wait_status "$ID" FAILED

echo "4) destino inexistente: debita, falha o credito e estorna (REFUNDED)"
BEFORE=$(balance "$ALICE")
ID=$(transfer "smoke-$RANDOM-4" "$ALICE" "00000000-0000-0000-0000-000000000001" 5.00 | json_field id)
wait_status "$ID" REFUNDED
AFTER=$(balance "$ALICE")
[ "$BEFORE" = "$AFTER" ] || { echo "FALHOU: saldo mudou ($BEFORE -> $AFTER)"; exit 1; }
echo "   Alice voltou ao saldo $AFTER"

echo "OK"
