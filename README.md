# Car Search

A backend service that lets a shopper search a used-car catalogue by typing what they want:

```
Show SUVs under 15L
Diesel automatic cars below 80k km
Family cars with high safety ratings
```

Java 17, Spring Boot 3.3, JPA, H2 in-memory. Clone it and run it — there is no database to install
and no API key to obtain.

---

## The idea

The service is two halves with a hard line between them.

```
   "Family cars with high safety ratings"
                  |
                  v
   +-------------------------------+
   |  QueryParser                  |   the only component that reads language
   |  (stub today, LLM tomorrow)   |
   +-------------------------------+
                  |  ParsedQuery — untrusted
                  |  filters: [ {seats, OVER, 6}, {safetyRating, EQUALS, HIGH} ]
                  |  features: [ isofix ]
                  v
   +-------------------------------+
   |  ParsedQueryValidator         |   known columns? known tags? right types?
   +-------------------------------+   magnitudes resolved here, not upstream
                  |                     anything unusable is dropped and reported,
                  |                     not fatal
                  |  SearchCriteria — validated, typed
                  |  seats >= 6, safetyRating >= 4, isofix
                  v
   +-------------------------------+
   |  VehicleSpecificationBuilder  |   one parameterised query
   +-------------------------------+
                  |
                  v
              PostgreSQL/H2
```

**The language half never touches the database.** `QueryParser` takes a string and returns
structure. It has no repository, no `EntityManager`, no connection — so no implementation of it,
however it is built, can emit SQL or read a row. It cannot be trusted to be correct, and it does
not have to be, because everything it produces crosses a validation boundary before it is used.

**The query half never reads language.** It takes an object whose field names are enum constants
and whose values are already the right Java types, and turns it into predicates through the JPA
Criteria API. No string is ever concatenated into SQL.

Three consequences worth naming:

- **Filters and features are separate lists.** A filter names a column; a feature names a tag. If
  they shared one list, the model would decide which kind each condition was, and the failure would
  be silent — `sunroof = true` as a column filter looks plausible and matches nothing.
- **Fuzzy words are resolved by this code, not the model.** The parser may return `HIGH` for a
  safety rating. `FuzzyBands` turns that into `>= 4` stars, because that is a domain fact about how
  Global NCAP results are read in India, not a language question. Same sentence, same threshold,
  every call.
- **Only four comparisons exist.** `UNDER`, `OVER`, `EQUALS`, `BETWEEN`, all bounds inclusive.
  Anything else is dropped at the boundary — the condition is set aside, the rest of the query
  runs, and the response names what was ignored.

---

## Quick start

Requires **Java 17+**. Maven comes down with the wrapper.

```bash
./mvnw spring-boot:run
```

The service starts on port 8080 and seeds 47 used-car listings on boot.

```bash
curl -G http://localhost:8080/api/v1/search \
     --data-urlencode "q=Show SUVs under 15L"
```

Run the tests:

```bash
./mvnw test
```

Browse the seeded data directly at <http://localhost:8080/h2-console> (JDBC URL
`jdbc:h2:mem:carsearch`, user `sa`, no password).

To watch the SQL the service builds, and the values bound to it:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="\
  -Dlogging.level.org.hibernate.SQL=DEBUG \
  -Dlogging.level.org.hibernate.orm.jdbc.bind=TRACE"
```

---

## API

### `GET /api/v1/search`

Search the catalogue with a natural-language query.

| Parameter | Type   | Required | Default | Notes                                  |
|-----------|--------|----------|---------|----------------------------------------|
| `q`       | string | yes      | —       | The shopper's query. 1–300 characters. |
| `page`    | int    | no       | `0`     | Zero-indexed page number.              |
| `size`    | int    | no       | `20`    | Results per page, 1–50.                |

Results are ordered by price ascending, then by id — a fixed order, so paging cannot repeat or
drop a car between pages.

#### `200 OK`

```bash
curl -G http://localhost:8080/api/v1/search \
     --data-urlencode "q=Family cars with high safety ratings" \
     --data-urlencode "size=2"
```

```json
{
  "query": "Family cars with high safety ratings",
  "interpretation": {
    "filters": [
      { "field": "seats",        "comparison": "OVER", "values": ["6"] },
      { "field": "safetyRating", "comparison": "OVER", "values": ["4"] }
    ],
    "features": ["isofix"],
    "ignoredFilters": [],
    "ignoredFeatures": []
  },
  "page": { "number": 0, "size": 2, "totalElements": 5, "totalPages": 3 },
  "results": [
    {
      "id": 36,
      "make": "Renault",
      "model": "Triber",
      "variant": "RXZ",
      "year": 2021,
      "price": 645000.00,
      "kilometres": 39600,
      "fuelType": "PETROL",
      "transmission": "MANUAL",
      "bodyType": "MUV",
      "seats": 7,
      "ownerCount": 1,
      "safetyRating": 4,
      "conditionGrade": "GOOD",
      "accidentHistory": "NONE",
      "city": "Ahmedabad",
      "colour": "Brown",
      "airbags": 4,
      "features": ["abs_ebd", "alloy_wheels", "android_auto", "apple_carplay",
                   "climate_control", "isofix", "parking_sensors", "rear_ac_vents",
                   "rear_camera", "roof_rails", "third_row_seating", "touchscreen"]
    }
  ]
}
```

**`interpretation` is part of the contract, not debug output.** Natural-language search fails in a
way keyword search does not: it can misread you and still return a confident page of cars. Handing
back what the sentence was understood to mean lets a shopper see `safetyRating OVER 4` and correct
it, and lets anyone reading a bug report tell a parsing problem from a stock problem without
reproducing anything. Note that it shows the *resolved* threshold — `HIGH` never appears, because
`HIGH` is not what ran.

All four lists are always present, empty when there is nothing to report. A client checking whether
anything was dropped shouldn't also have to handle an absent key.

#### Partial results

A condition that can't be honoured is **dropped, and the query runs on what remains**. A shopper
who asks for four things and gets three of them right sees results, not an error — which is only
safe because the response says exactly which one was ignored.

```bash
curl -G http://localhost:8080/api/v1/search \
     --data-urlencode "q=Red SUVs with a panoramic sunroof and mileage over 20 kmpl"
```

```json
{
  "interpretation": {
    "filters": [
      { "field": "colour",   "comparison": "EQUALS", "values": ["Red"] },
      { "field": "bodyType", "comparison": "EQUALS", "values": ["SUV"] }
    ],
    "features": ["panoramic_sunroof"],
    "ignoredFilters": [
      { "field": "mileage", "comparison": "OVER", "values": ["20"],
        "reason": "unknown field 'mileage'" }
    ],
    "ignoredFeatures": []
  },
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 },
  "results": [ { "make": "MG", "model": "ZS EV", "colour": "Red", "...": "..." } ]
}
```

Dropped filters and dropped feature tags are reported in **separate lists**, mirroring the split
the request already makes. They're different kinds of thing — a filter has a field, a comparison
and values; a feature has only a tag — and a single merged array would either lose fields or carry
nulls for half its entries.

Drops are logged at WARN. This matters: unusable conditions used to produce a `502`, so model drift
showed up as an error rate. Now that these return `200`, that log line is the only thing left that
surfaces a parser which has started inventing columns.

#### Errors

All errors are [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) problem documents.

| Status | When                                                              | Whose problem  |
|--------|-------------------------------------------------------------------|----------------|
| `400`  | `q` blank or too long, `size` out of range                          | The caller's   |
| `422`  | Nothing interpretable, or every condition was dropped                | The shopper's  |
| `502`  | The parser returned more conditions than any real query has          | **Ours**       |

A `422` arrives when nothing usable survives. Dropping is tolerant right up until nothing is left:
a query with no conditions would produce no `WHERE` clause and hand back the entire catalogue,
which is the failure that looks most like success. The response still lists what was thrown away —
a query that fails outright has to explain itself just as clearly as one that partially succeeds.

```bash
curl -G http://localhost:8080/api/v1/search \
     --data-urlencode "q=Any colour except white with teleport mode"
```

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

The `502` is now rare and means something specific: more than 20 filters or 20 features, which is
not a query with some bad parts to drop but a parser that has stopped working. There's no sensible
subset of 200 conditions to honour, so that one stays fatal.

---

## The parser contract

What the parser is allowed to return.

**Filters** — a field, a comparison, and one or two values:

| Field                        | Type                                       | Comparisons |
|------------------------------|--------------------------------------------|-------------|
| `make`, `model`, `city`, `colour` | text (matched case-insensitively)     | `EQUALS`    |
| `fuelType`                   | `PETROL DIESEL CNG HYBRID ELECTRIC`        | `EQUALS`    |
| `transmission`               | `MANUAL AUTOMATIC`                         | `EQUALS`    |
| `bodyType`                   | `HATCHBACK SEDAN SUV MUV`                  | `EQUALS`    |
| `conditionGrade`             | `EXCELLENT GOOD FAIR`                      | `EQUALS`    |
| `accidentHistory`            | `NONE MINOR MAJOR`                         | `EQUALS`    |
| `price`                      | whole rupees                               | all four    |
| `year`, `kilometres`, `seats`, `ownerCount`, `safetyRating`, `airbags` | integer | all four |

`UNDER` is `<=` and `OVER` is `>=`. Inclusive because listing prices cluster on round numbers — a
shopper asking for cars under 15 lakh expects to see the one priced at exactly 15,00,000. It is
also what lets "high safety rating" resolve to `OVER 4` and mean "4 stars and up".

**Features** — a flat list of tags such as `sunroof`, `isofix`, `camera_360`. Every tag must be in
the vocabulary configured under `carsearch.vocabulary.features`. Features live in a tag set rather
than as columns because the list is open-ended: adding "ventilated seats" should be a data change,
not an `ALTER TABLE` on a wide, hot table. The vocabulary is an allow-list rather than a Java enum
for the same reason — a new feature is a config change plus a prompt change, with no recompile.

A tag can be valid and match nothing. That is a legitimate "we don't stock one", not an error.

**Magnitudes** — instead of a value, an ordered field may carry `LOW`, `MEDIUM` or `HIGH`, paired
with `EQUALS`. This code decides what those mean:

| Field          | `LOW`         | `MEDIUM`  | `HIGH`         |
|----------------|---------------|-----------|----------------|
| `safetyRating` | `<= 2`        | `= 3`     | `>= 4`         |
| `kilometres`   | `<= 30,000`   | —         | `>= 100,000`   |

The kilometre thresholds follow the valuation brackets used on the buying side. There is no
`MEDIUM` band for it: nobody asks for a "medium mileage" car, and the range between the two bands
is already expressible with `UNDER` and `OVER` carrying a real number. A level a field does not
define is rejected rather than snapped to the nearest band — guessing would answer a question the
shopper did not ask.

`price` deliberately has no bands. "Cheap" means under 5 lakh for a hatchback and under 15 for a
seven-seater, so a single threshold would be wrong for most of the catalogue. The model is expected
to resolve budget words into an actual rupee figure from context, and if it cannot, no filter is
better than an invented one.

---

## Trying it out

The parser is a stub for now — a lookup table of known sentences, so the whole pipeline runs
without an API key. It recognises these, plus a few phrasings of each:

| Query | Exercises |
|-------|-----------|
| `Show SUVs under 15L` | enum + money filter |
| `Diesel automatic cars below 80k km` | three ANDed filters |
| `Family cars with high safety ratings` | magnitude resolution + a feature tag |
| `Petrol hatchbacks with a sunroof` | filters + feature together |
| `First owner cars between 5 and 10 lakh` | `BETWEEN` |
| `Low kilometre automatic cars in Bengaluru` | magnitude on a second field, text filter |
| `Seven seater diesel SUVs with a 360 degree camera and cruise control` | two ANDed tags |
| `Accident free Tata cars with high safety ratings` | text + enum + magnitude |
| `Red SUVs with a panoramic sunroof and mileage over 20 kmpl` | **partial** — invented column dropped, rest runs (200) |
| `Cars with mileage over 20 kmpl` | **rejected** — nothing left to search (422) |
| `Any colour except white with teleport mode` | **rejected** — bad comparison + tag, nothing left (422) |

The last three return deliberately invalid structure. They are how the validation boundary can be
demonstrated by hand, without waiting for a real model to hallucinate — the first shows a query
running with one condition dropped, the other two show what happens when nothing usable is left.

Anything else returns `422` with the list of known phrasings.

---

## Layout

```
com.cars24.carsearch
├── api/            HTTP edge — one controller, response DTOs, error mapping
├── catalogue/      Vehicle entity, its enums, the repository
├── vocabulary/     the shared contract: VehicleField, Comparison, FuzzyBands, FeatureCatalogue
├── nlq/            language side — QueryParser, the untrusted ParsedQuery, caching, the stub
├── validation/     the boundary — ParsedQueryValidator, drop reasons
├── search/         query side — SearchCriteria, the specification builder, the service
└── config/         wiring — cache, parser assembly
```

`vocabulary` sits on its own because both halves depend on it and neither owns it. It is the
definition of what may be said: which columns are searchable, which comparisons exist, what `HIGH`
means, which feature tags are real. When the LLM lands, this same package is what generates its
prompt — so the vocabulary the model is told about and the vocabulary the validator enforces cannot
drift apart.

### Caching

Interpretations are cached, not results. Inventory turns over constantly, so caching the cars would
serve sold ones — but "SUVs under 15 lakh" means `bodyType = SUV AND price <= 1500000` today and
next month. The cache key is the query lowercased, trimmed and whitespace-collapsed. It sits in a
decorator around `QueryParser`, so the stub and the eventual LLM get identical caching without
either knowing about it.

### Testing

41 tests. The validator tests are mostly about what gets *dropped*, since every case there is
something a real model does: inventing a column, inventing a feature, putting a word where a number
belongs, using a comparison outside the contract. Each checks both halves — that the bad condition
didn't run, and that it came back reported. The endpoint tests run the full pipeline against
the seed data and assert both exact counts and properties — not just "14 cars" but "every one of
them is an SUV at or below 15 lakh", so a query returning the right number of wrong cars still
fails.

---

## Seed data

`src/main/resources/db/seed.sql` is **generated**, not hand-written. The source of truth is
`scripts/seed-data/catalogue.json` — one entry per model holding its body style, seats, fuels,
gearboxes, NCAP rating, three trim levels with their feature sets, and a reference used price.

```bash
python3 scripts/generate_seed.py     # or: ./mvnw -Pseed generate-resources
```

186 listings across 45 models, 13 makes, 10 cities and every fuel type. Each model appears three
to five times so a search narrows rather than bottoming out at one car. Listings of the same model
vary by year, trim, city, colour, owner count, condition and accident history; kilometres and price
are derived from the year, with the trim placing a listing inside its model-year price band.

The catalogue is weighted toward cars one to three years old, which is where used inventory
actually sits, with a thinning tail back to 2018. Generation is deterministic — the same catalogue
always produces the same file, so a change is a reviewable diff rather than a reshuffle, and the
checked-in seed file means an ordinary build never needs Python.

A narrow feature tag always carries its broad one: a car with `panoramic_sunroof` also has
`sunroof`, so a plain search for a sunroof matches it.

Price bands are representative of the Indian used-car market, sanity-checked against public
listings in September 2026. The catalogue is synthetic and no third-party data is included.

---

## Swapping in the LLM

One method:

```java
// config/ParserConfiguration.java
@Bean
public QueryParser queryInterpreter() {
    return new StubQueryParser();   // <- becomes the LLM-backed implementation
}
```

Everything downstream depends on the `QueryParser` interface. The caching, the validation, the
query building and the error handling are already in place and already tested against the stub, so
the new implementation inherits a pipeline that already refuses to trust it.

---

## Status

Working end to end: entity and repository, the parsed-query model, the stub parser, the validation
layer, the query builder, one search endpoint, and the seed script.

Not yet built: the LLM-backed parser, and `DESIGN.md`.
