# Car Search

A backend service that searches a used-car catalogue from a plain-English query — `Show SUVs under
15L`, `Diesel automatic cars below 80k km`, `Family cars with high safety ratings`. A parser turns
the sentence into structured conditions and ordinary code runs the query. **It ships with a stub
parser by default, so it clones and runs with no API key and no spend** — the stub recognises a
fixed list of demo queries and returns `422` for anything else. Setting one property and exporting
an `ANTHROPIC_API_KEY` switches to the real Claude-backed parser, which handles arbitrary input.

Design decisions and their reasoning live in [DESIGN.md](DESIGN.md).

---

## Setup

Requires **Java 17+**. Maven comes down with the wrapper; nothing else to install.

```bash
git clone <repo-url>
cd car-search
./mvnw spring-boot:run
```

Starts on **port 8080** and seeds 182 car listings into an in-memory H2 database on boot.

```bash
curl -G http://localhost:8080/api/v1/search --data-urlencode "q=Show SUVs under 15L"
```

Run the tests (57, no API key needed):

```bash
./mvnw test
```

---

## Switching to the Claude parser

Set the implementation property and export a key:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run -Dspring-boot.run.arguments=--carsearch.parser.implementation=claude
```

The key is read from the environment only. **It is never stored in the repo**, and local override
files that might hold one (`.env`, `application-local.yml`, `application-local.properties`,
`secrets.json`) are git-ignored. The application fails at startup with an explanatory message if
`claude` is selected without a key.

Configuration, in `application.yml` (datasource, JPA, H2 console and logging settings left out):

```yaml
carsearch:
  parser:
    implementation: stub        # stub | claude
    claude:
      model: claude-opus-5
      effort: low               # low | medium | high | xhigh | max; blank omits the parameter
      max-tokens: 2048
      timeout: 30s              # per attempt; with the SDK's two retries a search can wait ~90s plus backoff
  cache:
    spec: maximumSize=1000,expireAfterWrite=24h   # Caffeine spec for the interpretation cache
  vocabulary:
    features:                   # the feature-tag allow-list (38 tags)
      - abs_ebd
      # ...
    feature-notes:              # glosses for tags a model can't guess from the name
      # ...

spring:
  mvc:
    problemdetails:
      enabled: true             # Spring's own request errors come back as RFC 7807
```

`effort` is deliberately blankable — some models reject the parameter entirely, so leaving it empty
omits it from the request rather than sending something invalid.

### Queries the stub recognises

In the default `stub` mode only these are understood (plus a few phrasings of each). Anything else
returns `422`.

```
Show SUVs under 15L
Diesel automatic cars below 80k km
Family cars with high safety ratings
Petrol hatchbacks with a sunroof
First owner cars between 5 and 10 lakh
Low kilometre automatic cars in Bengaluru
Seven seater diesel SUVs with a 360 degree camera and cruise control
Accident free Tata cars with high safety ratings
Red SUVs with a panoramic sunroof and mileage over 20 kmpl
Cars with mileage over 20 kmpl
Any colour except white with teleport mode
```

---

## API

### `GET /api/v1/search`

| Parameter | Type | Required | Default | Notes |
|-----------|------|----------|---------|-------|
| `q` | string | yes | — | The search query. 1–300 characters. |
| `page` | int | no | `0` | Zero-indexed page number. |
| `size` | int | no | `20` | Results per page, 1–50. |

Results are ordered by price ascending, then by id.

```bash
curl -G http://localhost:8080/api/v1/search \
  --data-urlencode "q=Diesel automatic cars below 80k km"
```

Paging — page 2, five per page:

```bash
curl -G http://localhost:8080/api/v1/search \
  --data-urlencode "q=Diesel automatic cars below 80k km" \
  --data-urlencode "page=1" \
  --data-urlencode "size=5"
```

#### `200 OK`

Abbreviated — vehicle fields trimmed, two of 27 results shown:

```json
{
  "query": "Diesel automatic cars below 80k km",
  "interpretation": {
    "filters": [
      { "field": "fuelType",     "comparison": "EQUALS", "values": ["DIESEL"] },
      { "field": "transmission", "comparison": "EQUALS", "values": ["AUTOMATIC"] },
      { "field": "kilometres",   "comparison": "UNDER",  "values": ["80000"] }
    ],
    "features": [],
    "ignoredFilters": [],
    "ignoredFeatures": []
  },
  "page": { "number": 0, "size": 20, "totalElements": 27, "totalPages": 2 },
  "results": [
    { "id": 145, "make": "Tata", "model": "Nexon", "variant": "XM",
      "year": 2023, "price": 780000.00, "kilometres": 28500,
      "fuelType": "DIESEL", "transmission": "AUTOMATIC", "bodyType": "SUV",
      "safetyRating": 5,
      "features": ["abs_ebd", "android_auto", "apple_carplay", "touchscreen"] },

    { "id": 148, "make": "Tata", "model": "Nexon", "variant": "XZ+",
      "year": 2025, "price": 985000.00, "kilometres": 16500,
      "fuelType": "DIESEL", "transmission": "AUTOMATIC", "bodyType": "SUV",
      "safetyRating": 5,
      "features": ["abs_ebd", "alloy_wheels", "cruise_control", "sunroof", "..."] }
  ]
}
```

#### The `interpretation` block

What the query was understood to mean. All four lists are always present, empty when there is
nothing to report.

| Field | Type | Contains |
|-------|------|----------|
| `filters` | array | Column conditions that were applied. Each has `field`, `comparison` (`UNDER` / `OVER` / `EQUALS` / `BETWEEN`) and `values` (one entry, or two for `BETWEEN`). |
| `features` | array | Feature tags that were applied, as bare strings. |
| `ignoredFilters` | array | Conditions asked for but not applied. Each carries the original `field`, `comparison` and `values`, plus a `reason`. |
| `ignoredFeatures` | array | Feature tags asked for but not applied, each with `tag` and `reason`. |

Filters show the **resolved** condition, not the wording — "high safety rating" appears as
`safetyRating OVER 4`. See [DESIGN.md](DESIGN.md) for why the interpretation is returned to the
caller rather than kept internal.

#### Vehicle fields

`id`, `make`, `model`, `variant`, `year`, `price`, `kilometres`, `fuelType`
(`PETROL` / `DIESEL` / `CNG` / `HYBRID` / `ELECTRIC`), `transmission` (`MANUAL` / `AUTOMATIC`),
`bodyType` (`HATCHBACK` / `SEDAN` / `SUV` / `MUV`), `seats`, `ownerCount`, `safetyRating` (0–5),
`conditionGrade` (`EXCELLENT` / `GOOD` / `FAIR`), `accidentHistory` (`NONE` / `MINOR` / `MAJOR`),
`city`, `colour`, `airbags`, `features`.

---

## Errors

Errors the application produces are [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807)
problem documents: the ones in the table below, and Spring's own request errors (a missing `q`, a
non-numeric `page`, an unknown path, an unsupported method). Unexpected failures, and malformed
requests that Tomcat rejects before Spring sees them, are not.

| Status | Title | When |
|--------|-------|------|
| `400` | Invalid request | `q` is blank or over 300 characters, or `page`/`size` is out of range. |
| `400` | Bad Request | `q` is missing, or `page`/`size` is not a number. Spring's own request error. |
| `422` | Query not understood | The query could not be interpreted, or every condition in it had to be dropped. In `stub` mode, also returned for any query outside the recognised list. |
| `502` | Interpretation rejected | The parser returned more conditions than any real query has (over 20 filters or 20 features). |
| `503` | Interpreter unavailable | The Claude API could not be reached, timed out, returned an error status (such as 500, 429 or 401), refused the request, or returned output that did not match the schema. Retrying may succeed. |

A `422` caused by dropped conditions carries the same `ignoredFilters` and `ignoredFeatures` arrays
as a successful response:

```json
{
  "type": "about:blank",
  "title": "Query not understood",
  "status": 422,
  "detail": "None of the 2 condition(s) in that query could be used.",
  "instance": "/api/v1/search",
  "ignoredFilters": [
    { "field": "colour", "comparison": "NOT_EQUALS", "values": ["White"],
      "reason": "unknown comparison 'NOT_EQUALS'; allowed are UNDER, OVER, EQUALS, BETWEEN" }
  ],
  "ignoredFeatures": [
    { "tag": "teleport_mode", "reason": "unknown feature tag" }
  ]
}
```

---

## Seed data

182 listings across 45 models, 13 makes, 10 cities, every fuel type and 36 feature tags. Each model
appears three to five times, varying by year, trim, city, colour, owner count, condition and
accident history. The catalogue is weighted toward cars one to three years old, with a tail back to
2018; kilometres and price are derived from the year.

`src/main/resources/db/seed.sql` is **generated**, not hand-edited. The source is
`scripts/seed-data/catalogue.json` — one entry per model holding its body style, seats, powertrain
combinations, NCAP rating, model lifespan, three trim levels with their feature sets, and a
reference price.

```bash
python3 scripts/generate_seed.py          # or: ./mvnw -Pseed generate-resources
```

Generation is deterministic: the same catalogue always produces the same file. The seed file is
checked in, so an ordinary build never needs Python.

Price bands are representative of the Indian used-car market, sanity-checked against public listings
in September 2026. The catalogue is synthetic and no third-party data is included.

### Inspecting the data

The H2 console is **enabled on purpose** for this submission so the seeded data can be browsed
without a database client — <http://localhost:8080/h2-console>, JDBC URL `jdbc:h2:mem:carsearch`,
user `sa`, no password. It would not be enabled in a deployed service.

---

## Demo script

`scripts/demo-queries.sh` runs a fixed set of queries through the running service and prints the raw
output for each — the JSON Claude returned, then the full HTTP response. It covers negation, tag
matching, approximation and units, magnitude words, unsupported requests, and the three example
queries.

```bash
export ANTHROPIC_API_KEY=sk-ant-...

./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--carsearch.parser.implementation=claude \
  -Dspring-boot.run.jvmArguments="-Dlogging.level.com.cars24.carsearch.nlq.claude=DEBUG" \
  > /tmp/car-search.log 2>&1 &

./scripts/demo-queries.sh /tmp/car-search.log
```

The log argument is optional — pass it to see Claude's raw JSON above each response, omit it for
HTTP responses only. Edit the `QUERIES` array at the top of the script to change what runs.
