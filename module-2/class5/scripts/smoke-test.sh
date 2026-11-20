#!/usr/bin/env bash
# Smoke tests against a running stack (docker compose up -d --build or make start-infra + the app).
# Usage: ./scripts/smoke-test.sh [base-url]
# The itineraries are random, so the checks only verify status codes and the shape of the responses.

BASE_URL="${1:-http://localhost:8080/api/flights}"
MICROCKS_URL="${MICROCKS_URL:-http://localhost:8585}"
CATALOG_URL="${CATALOG_URL:-http://localhost:8081/api/catalog}"
API_KEY="${PRICING_API_KEY:-twa-pricing-local-key}"

failures=0
body_file=$(mktemp)
trap 'rm -f "$body_file"' EXIT

# Future dates, the search does not accept dates in the past
read -r DEPARTURE RETURN <<<"$(python3 -c 'import datetime as d; t = d.date.today(); print(t + d.timedelta(days=60), t + d.timedelta(days=70))')"
SEARCH="$BASE_URL/clusters/itineraries?from=BUE,MIA&to=MIA,BUE&departure=$DEPARTURE,$RETURN&adults=1&children=0&infants=0"

# check <description> <expected status> <curl arguments...>
check() {
  local description=$1 expected=$2
  shift 2
  local status
  status=$(curl -s -o "$body_file" -w '%{http_code}' "$@")
  if [ "$status" = "$expected" ]; then
    echo "  OK    $description [$status]"
  else
    echo "  FAIL  $description: expected $expected, got $status -> $(head -c 300 "$body_file")"
    failures=$((failures + 1))
  fi
}

# json <python expression over the variable d>
json() {
  python3 -c "import json, sys; d = json.load(open('$body_file')); print($1)" 2>/dev/null
}

echo "Application: $BASE_URL"
check "health" 200 "$BASE_URL/actuator/health"

echo "Search"
check "round trip search" 200 "$SEARCH&amount=3"
SEARCH_ID=$(json "d['id']")
ITINERARY_ID=$(json "d['itineraries'][0]['id'] if d['itineraries'] else ''")
if [ -z "$ITINERARY_ID" ]; then
  echo "  FAIL  the search does not return itineraries"
  failures=$((failures + 1))
fi
check "one way business search with filters" 200 "$BASE_URL/clusters/itineraries?from=BUE&to=SCL&departure=$DEPARTURE&adults=2&children=1&infants=0&cabin=BUSINESS&maxStops=1&sortBy=DURATION"
check "invalid search (date in the past)" 400 "$BASE_URL/clusters/itineraries?from=BUE&to=MIA&departure=2020-01-01&adults=1&children=0&infants=0"

# The search is saved asynchronously
sleep 1
check "next page of the search" 200 -G "$SEARCH&amount=3&offset=1" --data-urlencode "id=$SEARCH_ID"
if [ -n "$ITINERARY_ID" ]; then
  check "itinerary of the search" 200 "$BASE_URL/clusters/$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$SEARCH_ID")/itineraries/$ITINERARY_ID"
fi
check "itinerary of an unknown search" 404 "$BASE_URL/clusters/unknown/itineraries/unknown"

echo "Pricing administration"
check "commercial policies without API key" 401 "$BASE_URL/pricing/commercial-policies"
check "commercial policies" 200 -H "X-Api-Key: $API_KEY" "$BASE_URL/pricing/commercial-policies"
check "unknown commercial policy" 404 -H "X-Api-Key: $API_KEY" "$BASE_URL/pricing/commercial-policies/999999"

echo "Catalog service: $CATALOG_URL"
check "catalog health" 200 "$CATALOG_URL/actuator/health"
check "city" 200 "$CATALOG_URL/cities/BUE"
if [ "$(json "d['timeZone']")" != "America/Argentina/Buenos_Aires" ]; then
  echo "  FAIL  the city BUE does not have its time zone"
  failures=$((failures + 1))
fi
check "unknown city" 404 "$CATALOG_URL/cities/XXX"
check "several cities" 200 "$CATALOG_URL/cities?codes=BUE,mia,XXX"
if [ "$(json "','.join(c['code'] for c in d)")" != "BUE,MIA" ]; then
  echo "  FAIL  several cities: expected BUE,MIA, got $(head -c 300 "$body_file")"
  failures=$((failures + 1))
fi
check "several cities without codes" 400 "$CATALOG_URL/cities"

echo "Providers (Microcks): $MICROCKS_URL"
for provider in alpha beta gamma; do
  check "provider $provider" 200 "$MICROCKS_URL/rest/provider-$provider/1.0/itineraries?origin=BUE&destination=MIA&departureDate=$DEPARTURE&returnDate=$RETURN&adults=1"
done
check "mock of the catalog: city" 200 "$MICROCKS_URL/rest/twa-flights-catalog/1.0/cities/BUE"
check "mock of the catalog: unknown city" 404 "$MICROCKS_URL/rest/twa-flights-catalog/1.0/cities/XXX"
check "mock of the catalog: one code" 200 "$MICROCKS_URL/rest/twa-flights-catalog/1.0/cities?codes=MIA"
if [ "$(json "','.join(c['code'] for c in d)")" != "MIA" ]; then
  echo "  FAIL  mock of the catalog: expected MIA, got $(head -c 300 "$body_file")"
  failures=$((failures + 1))
fi

if [ "$failures" -gt 0 ]; then
  echo "$failures check(s) failed"
  exit 1
fi
echo "All the checks passed"
