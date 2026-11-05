#!/usr/bin/env bash
set -euo pipefail

base="http://127.0.0.1:8080"
health=$(curl -fsS "$base/health")
[[ "$health" == "TaskFlow is running" ]] || { echo "health check failed" >&2; exit 1; }

created=$(curl -fsS -X POST "$base/tasks" -H 'Content-Type: application/json' \
  -d '{"title":"Smoke test"}')
id=$(printf '%s' "$created" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')

curl -fsS "$base/tasks/$id" > /dev/null
curl -fsS "$base/tasks" > /dev/null

ok_status=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$base/tasks/$id/status" \
  -H 'Content-Type: application/json' -d '{"status":"InProgress","expectedVersion":0}')
[[ "$ok_status" == "200" ]] || { echo "expected 200, got $ok_status" >&2; exit 1; }

stale_status=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$base/tasks/$id/status" \
  -H 'Content-Type: application/json' -d '{"status":"Cancelled","expectedVersion":0}')
[[ "$stale_status" == "409" ]] || { echo "expected 409, got $stale_status" >&2; exit 1; }

echo "Smoke checks passed: health, create, get, list, update, version conflict."
