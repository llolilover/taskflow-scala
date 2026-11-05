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

history=$(curl -fsS "$base/tasks/$id/history")
printf '%s' "$history" | python3 -c 'import json,sys; rows=json.load(sys.stdin); assert len(rows)==1 and rows[0]["fromStatus"]=="Backlog" and rows[0]["toStatus"]=="InProgress"'
# An isolated assignee ID makes this test independent of existing tasks in PostgreSQL.
assignee=$(python3 -c 'import uuid; print(uuid.uuid4())')
for title in "Page A" "Page B"; do
  curl -fsS -X POST "$base/tasks" -H 'Content-Type: application/json' \
    -d "{\"title\":\"$title\",\"assigneeId\":\"$assignee\"}" > /dev/null
done

first=$(curl -fsS "$base/tasks?assignee=$assignee&limit=1")
cursor=$(printf '%s' "$first" | python3 -c 'import json,sys; data=json.load(sys.stdin); assert len(data["items"])==1; assert data["nextCursor"]; print(data["nextCursor"])')
second=$(curl -fsS "$base/tasks?assignee=$assignee&limit=1&cursor=$cursor")
printf '%s' "$second" | python3 -c 'import json,sys; data=json.load(sys.stdin); assert len(data["items"])==1; assert data["nextCursor"] is None'

invalid=$(curl -s -o /dev/null -w '%{http_code}' "$base/tasks?limit=101")
[[ "$invalid" == "400" ]] || { echo "expected 400 for invalid limit, got $invalid" >&2; exit 1; }

echo "Smoke checks passed: health, CRUD, version conflict, history, filtered keyset pagination."
