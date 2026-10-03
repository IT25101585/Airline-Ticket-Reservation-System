#!/usr/bin/env bash
set -euo pipefail

# Run this against the local SQL Server-backed app:
#   ./scripts/uat-smoke.sh
# It does not start, stop, migrate, or reconfigure the application.
BASE_URL="${BASE_URL:-http://localhost:8080}"
CUSTOMER_EMAIL="${CUSTOMER_EMAIL:-demo@skylankaair.com}"
CUSTOMER_PASSWORD="${CUSTOMER_PASSWORD:-demo123}"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

status() {
  curl -sS -o /dev/null -w '%{http_code}' "$@"
}

test_status() {
  local expected="$1"
  shift
  local actual
  actual="$(status "$@")"
  [[ "$actual" == "$expected" ]] || {
    echo "FAIL: expected HTTP $expected, got $actual for $*" >&2
    exit 1
  }
  echo "PASS: HTTP $expected $*"
}

test_status 200 "$BASE_URL/"
test_status 200 "$BASE_URL/search"
test_status 302 "$BASE_URL/dashboard"

login_json="$(curl -sS -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$CUSTOMER_PASSWORD\"}")"
token="$(printf '%s' "$login_json" | jq -r '.accessToken')"
[[ -n "$token" && "$token" != "null" ]] || {
  echo "FAIL: API login did not return a JWT" >&2
  exit 1
}
echo "PASS: API login returned a bearer token"

test_status 200 "$BASE_URL/api/me" -H "Authorization: Bearer $token"
test_status 401 "$BASE_URL/api/me"

curl -fsS -c "$tmp/cookies" -X POST "$BASE_URL/login" \
  --data-urlencode "email=$CUSTOMER_EMAIL" \
  --data-urlencode "password=$CUSTOMER_PASSWORD" \
  -o /dev/null
test_status 200 "$BASE_URL/dashboard" -b "$tmp/cookies"

echo "UAT smoke suite passed. Complete the booking/payment/operations cases in docs/UAT-CHECKLIST.md."