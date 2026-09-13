#!/usr/bin/env bash
# Runs the demo query set through the live service and prints raw output for each:
# the raw JSON Claude returned, then the full HTTP response.
#
#   export ANTHROPIC_API_KEY=sk-ant-...
#   ./mvnw spring-boot:run -Dspring-boot.run.arguments=--carsearch.parser.implementation=claude \
#       -Dspring-boot.run.jvmArguments="-Dlogging.level.com.cars24.carsearch.nlq.claude=DEBUG" \
#       > /tmp/car-search.log 2>&1 &
#   ./scripts/demo-queries.sh /tmp/car-search.log

set -uo pipefail
BASE="${BASE:-http://localhost:8080}"
LOG="${1:-}"

QUERIES=(
  # negation -- the silent-failure risk
  "not white"
  "anything except diesel"
  "petrol or CNG"
  # tag matching
  "cars with a reverse camera"
  "a sunroof"
  "good brakes"
  # approximation and units
  "around 15 lakhs"
  "roughly 8L"
  "under 80k km"
  # fuzzy bands
  "low mileage family car"
  "high safety"
  # unsupported: one concrete, one vague
  "cars with 20 kmpl"
  "cars near a metro station"
  # the three examples from the brief
  "SUVs under 15 lakhs"
  "diesel automatic below 80k km"
  "family cars with high safety ratings"
)

for q in "${QUERIES[@]}"; do
  printf '\n════════════════════════════════════════════════════════════════\n'
  printf 'QUERY: %s\n' "$q"
  printf '════════════════════════════════════════════════════════════════\n'

  mark=0
  [ -n "$LOG" ] && [ -f "$LOG" ] && mark=$(wc -l < "$LOG")

  code=$(curl -s -o /tmp/demo-resp.json -w '%{http_code}' -G "$BASE/api/v1/search" \
         --data-urlencode "q=$q" --data-urlencode "size=3")

  if [ -n "$LOG" ] && [ -f "$LOG" ]; then
    raw=$(tail -n +$((mark + 1)) "$LOG" | grep -o 'Claude raw output.*' | head -1)
    [ -n "$raw" ] && printf -- '--- raw model output ---\n%s\n\n' "$raw"
  fi

  printf -- '--- HTTP %s ---\n' "$code"
  python3 -m json.tool < /tmp/demo-resp.json 2>/dev/null || cat /tmp/demo-resp.json
done
