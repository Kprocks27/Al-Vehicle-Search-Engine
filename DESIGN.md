# Design

Why the service is built the way it is. Each entry is a decision: what was chosen, why, and what
it cost. It assumes you have read the code.

---

## 1. The core split

**Chosen:** a language model turns the shopper's sentence into a short list of conditions.
Ordinary code checks those conditions and runs the query. The model never sees the database or the
inventory, and never writes SQL.

The parser's whole contract is `String` in, `ParsedQuery` out (`QueryParser`). The interface
hands over no repository and no connection, and no existing implementation takes one. Nothing stops
a future implementation from taking one; the compiler doesn't enforce it.

**Why:** the model will sometimes be wrong. If all it can produce is a list of conditions, being
wrong means a bad condition, which code can check.

**Given up:** anything that doesn't fit a flat AND of simple conditions (§4, §7).

---

## 2. The components

Four packages do the work. `VehicleSearchService` calls the first three in order; `vocabulary` is
the contract the others read.

| Package | Takes | Hands out |
|---|---|---|
| `nlq` | the raw sentence | a `ParsedQuery` of plain strings, not yet trusted |
| `validation` | a `ParsedQuery` | an `Interpretation`: the `SearchCriteria` that will run, plus what was dropped and why |
| `search` | `SearchCriteria` | a JPA `Specification`, run against the repository, returning a page of results |
| `vocabulary` | nothing at runtime | the contract: fields, comparisons, magnitude bands and feature tags, read by both the prompt and the validator |

`nlq` has two parsers, chosen by `carsearch.parser.implementation`: `stub` (a fixed table of demo
sentences, the default) and `claude` (one Messages API call per new sentence, up to three when the SDK retries a failure, with output held to a
JSON schema). Both sit behind the same cache. `api`, `catalogue` and `config` are HTTP, the entity,
and wiring.

---

## 3. Containment

Model output is treated like any other user input: untrusted until checked.

### Parser output is plain strings

**Chosen:** `ParsedFilter` (and `LlmFilter`, which Claude's JSON is read into) holds field,
comparison and values as `String`s, including the comparison.

**Why:** a typed enum fails inside Jackson on the first bad value. The whole response is lost at
that point, so there is no way to report each bad condition separately. As strings, everything
reaches the validator and each condition is judged on its own.

**Given up:** compiler checks between the parser and the validator. The validator does the typing
by hand.

### The validator is the only way in

**Chosen:** the parser returns `ParsedQuery`. `ParsedQueryValidator` is the only code that
produces a `SearchCriteria`, and the query builder accepts nothing else.

**Why:** validation can't be skipped by any path that exists.

**Given up:** the compiler doesn't enforce it. `SearchCriteria` is a public record, so a future
caller could construct one directly. And two types that describe nearly the same thing.

### SearchCriteria holds only what runs

**Chosen:** dropped conditions live on `Interpretation`, next to `SearchCriteria`, not inside it.

**Why:** the builder is never handed a condition we decided not to honour.

**Given up:** one more wrapper for the service to unpack.

### One parameterised query

**Chosen:** `VehicleSpecificationBuilder` builds the query through the Criteria API. Column names
come from `VehicleField` constants and values are bind parameters. All conditions are ANDed, and
each feature tag is its own correlated subquery (Hibernate renders `isMember` as
`? in (select … from vehicle_features …)`).

**Why:** no string from the model becomes SQL, even if validation were bypassed. A subquery per tag,
rather than a join, keeps one row per car, so paging counts stay right.

**Given up:** OR and nesting. (Spring Data also runs a count query for paging, and loads a page's
tags in one batched query.)

### One source for both ends of the contract

**Chosen:** the prompt's list of fields, comparisons, magnitude levels and tags is generated from
the `vocabulary` package (`SearchContract`), and so is the output schema's list of comparisons
(`QuerySchema`, in `nlq.claude`). The validator checks against the same enums and the same tag list
(`carsearch.vocabulary.features`).

**Why:** a hand-written field list in the prompt drifts from the validator the first time a column
is added. `SearchContractTest` fails if the prompt stops naming a field, comparison or tag.

**Given up:** the vocabulary half of the prompt can't be edited by hand. The rules half
(`prompt/query-parser.md`) is still hand-written.

### Schema keys in a fixed order

**Chosen:** `QuerySchema` pins the key order so `field` and `comparison` come before `values`.
`QuerySchemaTest` checks it.

**Why:** with `values` first, the model had to emit the numbers before naming the field or
comparison, and sometimes emitted an empty string instead: 7 malformed responses in 39 completed calls
before, 0 in 39 after, across 80 live calls with the cache bypassed and the app restarted between
batches.

**Given up:** nothing at runtime, but the order is now load-bearing (§8).

---

## 4. Ambiguity

### Exactly four comparisons

**Chosen:** `UNDER` (≤), `OVER` (≥), `EQUALS`, `BETWEEN` (both ends included). No not-equals, no
one-of.

**Why:** each has an obvious SQL form and an obvious trigger phrase, and the builder only ever ANDs
a flat list. Bounds include the limit because listing prices cluster on round numbers.

**Given up:** negation and alternatives. The prompt has the model report those under a made-up
field name (`colourExclusion`), which the validator drops and the response shows as ignored.

### Thresholds belong to the code

**Chosen:** for a size described in words ("low mileage", "high safety"), the model returns a level
(`LOW`, `MEDIUM` or `HIGH`) paired with `EQUALS`. `FuzzyBands` turns the level into a real
comparison:

| Field | LOW | MEDIUM | HIGH |
|---|---|---|---|
| `safetyRating` | `UNDER 2` | `EQUALS 3` | `OVER 4` |
| `kilometres` | `UNDER 30000` | none | `OVER 100000` |

On a number field, a level the field doesn't define, a level on a field with no bands, or a level
with any comparison other than `EQUALS` is dropped, not rounded to the nearest band. On text and
choice fields a word like `LOW` is read as a literal value. Global NCAP rates adult occupant
protection from 0 to 5 stars
([Global NCAP adult occupant protection protocol, v1.1.1, August 2025](https://www.globalncap.org/s/AOP-protocol-2025.pdf),
section 6.5). HIGH is 4 stars and up. The 0–5 scale is Global NCAP's; the cut-off is ours.

**Why:** the mapping from level to threshold is fixed in code, so what HIGH means never drifts
between calls. The model's reading of a sentence can still vary. Changing a threshold is a code
diff, not a prompt edit.

**Given up:** context. "Low mileage" means the same for a 2018 car and a 2024 one.

### Kilometre bands

**Chosen:** LOW is ≤ 30,000 km, HIGH is ≥ 100,000 km, and there is no MEDIUM.

**Why:** these are the end points of Cars24's published brackets: under 30,000 km is "lightly
used" and gets the best offers, and anything above 100,000 km "sees the steepest cut"
([Cars24 used-car valuation](https://www.cars24.com/used-car-valuation/)). No MEDIUM because
shoppers don't ask for "medium mileage", and the middle can already be expressed with real numbers.

**Given up:** a word for the range in between.

### A rough price becomes a ceiling

**Chosen:** an approximate figure ("around 15 lakhs") becomes `price UNDER 1500000`. `BETWEEN` is
used only when both ends are stated. Price has no bands: for a price word with no figure
("cheap", "budget"), the prompt tells the model to use a rupee figure only if it can infer one from
context, and otherwise to emit no price filter. A level sent anyway is dropped and reported.

**Why:** a range around the figure needs a width nobody gave. "Cheap" depends on the segment.

**Given up:** cars a little over the stated figure.

### Features are tags

**Chosen:** equipment is a tag set in a side table (`vehicle_features`), checked against an
allow-list of 38 tags in `application.yml`. Every requested tag must be present.

**Why:** a new feature is a config and data change, not a migration on the main table.

**Given up:** tags are yes or no only.

### Airbags are a column

**Chosen:** `airbags` is a whole-number field, not a tag.

**Why:** it is a count. "At least six airbags" needs `OVER`, which a tag can't express.

**Given up:** a migration if another countable feature needs the same treatment.

### Narrow tags carry their broad tag

**Chosen:** a car with `camera_360` also has `rear_camera`; `panoramic_sunroof` implies `sunroof`;
`front_parking_sensors` implies `parking_sensors`. The notes in
`carsearch.vocabulary.feature-notes` tell the model to use the broad tag unless the shopper names
the narrow one.

**Why:** a plain "sunroof" search finds panoramic ones too, without needing OR.

**Given up:** the rule lives in the catalogue data. Every listing in the seed follows it, but
nothing checks it at load time.

---

## 5. Failure

### Drop, don't fail

**Chosen:** a condition that can't be used (unknown field or tag, wrong value type, wrong number of
values, backwards range) is dropped, and the query runs on what remains.

**Why:** a shopper who asks for four things and gets three right should see results, not an error.

**Given up:** results can be wider than asked for, which is why the next entry exists.

### Always say what was applied and what was ignored

**Chosen:** every 200 response carries `filters`, `features`, `ignoredFilters` and
`ignoredFeatures`, always present, empty when there is nothing to report. A 422 caused by dropped
conditions carries `ignoredFilters` and `ignoredFeatures`. Other errors carry none of them. Applied filters show the resolved condition (`safetyRating OVER 4`), not the
wording.

**Why:** a dropped filter widens results. Dropped silently, it looks the same as a filter that
matched everything.

**Given up:** a larger response, and clients have to read the ignored lists.

### Two things stay fatal

**Chosen:** more than 20 filters or 20 features (`MAX_FILTERS`, `MAX_FEATURES` in
`ParsedQueryValidator`) rejects the whole parse. So does a parse that leaves nothing to search.

**Why:** 21 filters, or 21 features, isn't a query with some bad parts; it is a parser that has
stopped working.
An empty WHERE clause returns the whole catalogue and looks like a working search.

**Given up:** a parse with 21 filters fails even if 20 of them are fine.

### Status codes

| Code | Title | Means |
|---|---|---|
| 422 | Query not understood | Nothing usable was left: every condition was dropped, the model returned nothing, or (stub only) the sentence isn't in its table. The shopper's to rephrase. |
| 502 | Interpretation rejected | The parser returned more than 20 filters or more than 20 features. Our fault; logged at ERROR. |
| 503 | Interpreter unavailable | Claude couldn't be reached, timed out, returned an error status (500, 429, 401), refused, or returned output that didn't match the schema. The timeout (`carsearch.parser.claude.timeout`, 30s) is per attempt; with the SDK's two retries a search can wait about 90 seconds plus backoff. The same query may work next time. |

### Two log streams

**Chosen:** `VehicleSearchService` logs drops in two categories (`DropCategory`). An unknown field or
tag is `UNSUPPORTED_REQUEST`, logged at INFO: the shopper asked for something we don't hold. A
contract break on a known field (bad comparison, wrong value count, wrong type, a band that doesn't
exist, a blank tag) is `PARSER_DRIFT`, logged at WARN.

**Why:** unsupported requests have a normal baseline. Drift should be zero, so any of it is worth
an alert.

**Given up:** a value outside a known enum (`bodyType CONVERTIBLE`) counts as drift, even when the
shopper really did ask for it.

---

## 6. Data

### The shape problem

**Chosen:** each model appears 3 to 5 times (12 models three times, 19 four times, 14 five times),
varying year, trim, city, colour, owner count, condition and accident history. Fuel and gearbox are
drawn as a pair from combinations the model was actually sold with.

**Why:** with one listing per model, a query with two or three conditions usually matches nothing.

**Given up:** 182 rows is still small. Narrow queries can still come back empty.

Totals: 182 listings, 45 models, 13 makes, 10 cities, 36 of the 38 tags in use. `seed.sql` is
generated by `scripts/generate_seed.py` from `scripts/seed-data/catalogue.json`, with a fixed RNG
seed (24), so regenerating it gives a reviewable diff.

### Price

**Chosen:** each model has one reference price for a 2022 car (`refPrice2022`). Other years move by
7.4% per model year (`YEAR_STEP`), a step measured across adjacent model years in public listings
and applied to a used price, not a new-car price. Trim scales it by 0.84, 1.00 or 1.19; kilometres (against
10,000 a year), condition, accident history and owner count each move it by up to 10%. Rounded to
₹5,000. The result runs from ₹2.15 lakh to ₹36.65 lakh, median ₹9 lakh. Price bands per model-year
were sanity-checked against public used-car listings in September 2026.

**Why:** one used-market reference price per model, stepped by a rate measured between adjacent
model years in real listings, with the results checked against published ranges. The rejected
alternative was a curve anchored on new-car prices: those have shifted since 2019 and don't exist
for discontinued models.

**Given up:** real prices vary for reasons the formula doesn't model.

### Age

**Chosen:** weighted toward cars one to three years old (`YEAR_WEIGHTS`, heaviest at 2024, thinning
back to 2018), and limited to years the model was on sale (`fromYear`, `toYear`). 59% of listings
are from 2023 to 2025.

**Why:** my judgement, not a sourced fact: a catalogue with nothing newer than 2023 looks stale to
anyone who knows this market.

**Given up:** older stock is thin: 12 listings from 2018 and 2019.

### Odometer

**Chosen:** each listing samples its own yearly running, 3,000 to 17,000 km. The result runs from
3,000 to 121,000 km, median 27,250. 6 listings fall in the HIGH band (≥ 100,000).

**Why:** a wide spread is what makes a kilometre filter actually separate cars.

**Given up:** the HIGH band has very few cars to match.

### Synthetic

The catalogue is synthetic and contains no third-party data.

---

## 7. What I deliberately didn't build

**NOT_EQUALS and ONE_OF**
- What: "not white", "petrol or CNG".
- Why cut: they pull the model toward set logic and the builder toward negation and nesting. Today they are reported as unsupported.
- Would take: two `Comparison` constants, a NOT and an IN predicate in the builder, and a rewrite of the prompt's negation section.

**Variant as a searchable field**
- What: trim names like "SX(O)" or "ZX".
- Why cut: trim names don't compare across makes, and what a trim means is already in its tags.
- Would take: one `VehicleField` entry. For "top trim" to work across makes, the base/mid/top tier in `catalogue.json` would need to become a column.

**Semantic caching**
- What: reuse a cached interpretation for a sentence that means the same as one already seen.
- Why cut: a near match serves another query's conditions, and nothing in the response would show it.
- Would take: an embedding per query, a similarity threshold, and a set of labelled paraphrases to tune it on.

**A second LLM call to summarise results**
- What: a line of prose above the results.
- Why cut: a second model call on every query, for what the `interpretation` block already says in structured form.
- Would take: a call after the database query, given only the returned rows.

**Retry on bad model output**
- What: asking Claude again when its output is malformed.
- Why cut: designed and left unbuilt. The schema holds the output's shape, and a malformed condition is already dropped and reported rather than raising an error, so a retry would add latency for a failure the system already handles honestly. The SDK's own retry on connection and server errors is left at its default.
- Would take: a bounded loop around the call in `ClaudeQueryParser`.

**Machine-readable codes on drop reasons**
- What: `reason` is free text, and `DropCategory` isn't sent to the client.
- Why cut: the only reader today is a person.
- Would take: an enum code on `DroppedFilter` and `DroppedFeature`, sent next to `reason`.

**The demand signal.** The INFO line for unsupported requests ("asked for N thing(s) the catalogue
cannot express") records every unknown field and tag the model reports, such as
`fuelEfficiencyKmpl` or `massage_seats`. Added up over time, that is a list of what to build next.
It only sees what the model reports: vague requests ("reliable", "good condition") are emitted as
nothing by design, and values outside a known enum go to the drift stream instead (§5).

---

## 8. Known limitations

- **Safety ratings.** Ratings are per model, not per car: every listing of a model shares one value from `catalogue.json`. For models with no published Global NCAP result, that value is representative, not real. The star scale is Global NCAP's and is cited in §4; the per-model values are partly synthetic. Would change: record whether each rating is published, and keep representative ones out of HIGH.
- **Schema key order is load-bearing, not cosmetic.** The order of keys in `QuerySchema` is the order the model decides in, at the filter level and the top level. A new key added in the wrong place, or a switch back to an unordered map, could bring the empty-`values` failure (§3) back. `QuerySchemaTest` fails if today's order changes, but it can't judge where a new key belongs: a key has to come after the keys the model needs to decide first.
- **"Family car" means `seats OVER 6`.** This is a prompt rule with no external source; in this seed it means the 41 seven-seaters. Would change: find a source for it, or treat "family car" as a vague word and emit nothing.
- **Exact-match caching.** The cache key is the sentence lowercased with spaces collapsed (`QueryNormalizer`), so every paraphrase is a new Claude call. Would change: measure the hit rate before deciding on anything looser.
- **Tags on nearly every car.** `abs_ebd` is on every listing, `touchscreen` on 97%, `android_auto` and `apple_carplay` on 94%, so asking for them narrows almost nothing. Would change: tell the shopper when a tag didn't narrow the result.
