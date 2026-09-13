#!/usr/bin/env python3
"""Generates src/main/resources/db/seed.sql from scripts/seed-data/catalogue.json.

Run it with:  python3 scripts/generate_seed.py
or via Maven: ./mvnw -Pseed generate-resources

The catalogue holds one entry per model -- body, seats, fuels, gearboxes, NCAP rating, three
trim levels with their feature sets, and a reference used price for a 2022 example. This script
expands each model into several listings by varying year, city, colour, owner count, condition
and accident history, and derives kilometres and price from the year.

Deterministic: a fixed RNG seed means the same catalogue always produces the same seed.sql, so a
regeneration is a reviewable diff rather than a fresh shuffle, and tests can rely on the data.

Price bands are representative of the Indian used-car market, sanity-checked against public
listings in September 2026. The catalogue is synthetic and no third-party data is included.
"""
import json
import os
import random
from collections import defaultdict

TODAY = 2026
RNG_SEED = 24
OUT = "src/main/resources/db/seed.sql"
CATALOGUE = "scripts/seed-data/catalogue.json"

# Weighted toward one to three years old, which is where organised used inventory actually sits,
# with a thinning tail back to 2018.
YEAR_WEIGHTS = {2025: 18, 2024: 20, 2023: 18, 2022: 13, 2021: 11, 2020: 8, 2019: 7, 2018: 5}

# A car one year newer sells for about 7% more, measured across adjacent model years in public
# listings. Flatter than new-car depreciation because the steepest drop happens before a car
# reaches the used market at all.
YEAR_STEP = 1.074
REF_YEAR = 2022

# Where a trim sits inside its model-year band. The spread is wide on purpose: a base and a top
# trim of the same car in the same year genuinely differ by this much, and that spread is most of
# why a model-year band looks wide.
TRIM_MULTIPLIER = {"base": 0.84, "mid": 1.00, "top": 1.19}

# Annual running, sampled per listing rather than fixed. The range is what separates a second car
# from a highway commuter, and it is what makes an odometer filter discriminate.
KM_PER_YEAR = (3000, 17000)
EXPECTED_KM_PER_YEAR = 10000

CONDITION_MULTIPLIER = {"EXCELLENT": 1.04, "GOOD": 1.00, "FAIR": 0.93}
ACCIDENT_MULTIPLIER = {"NONE": 1.00, "MINOR": 0.96, "MAJOR": 0.90}
OWNER_MULTIPLIER = {1: 1.03, 2: 1.00, 3: 0.95}

CITIES = ["Bengaluru", "Mumbai", "Delhi", "Pune", "Hyderabad",
          "Chennai", "Gurugram", "Kolkata", "Jaipur", "Ahmedabad"]
COLOURS = ["White", "Silver", "Grey", "Blue", "Red", "Black", "Brown", "Teal"]


def pick_year(rng, model):
    """Picks a model year the car could actually carry.

    A listing for a 2019 Carens is nonsense -- the model went on sale in 2022 -- and the same goes
    the other way for a model that has since been discontinued. The catalogue carries both ends,
    and the age weighting is applied only across the years in between.
    """
    years = [y for y in YEAR_WEIGHTS if model["fromYear"] <= y <= model["toYear"]]
    if not years:
        years = [max(model["fromYear"], min(model["toYear"], 2022))]
    return rng.choices(years, weights=[YEAR_WEIGHTS.get(y, 1) for y in years])[0]


def pick_owner(rng, age):
    if age <= 3:
        return rng.choices([1, 2], weights=[88, 12])[0]
    if age <= 6:
        return rng.choices([1, 2, 3], weights=[55, 38, 7])[0]
    return rng.choices([1, 2, 3], weights=[30, 46, 24])[0]


def pick_condition(rng, age, km):
    hard_life = km > age * 14000
    if age <= 2 and not hard_life:
        return rng.choices(["EXCELLENT", "GOOD"], weights=[78, 22])[0]
    if age <= 5:
        return rng.choices(["EXCELLENT", "GOOD", "FAIR"], weights=[42, 50, 8])[0]
    return rng.choices(["EXCELLENT", "GOOD", "FAIR"], weights=[14, 56, 30])[0]


def pick_accident(rng, age):
    weights = [90, 9, 1] if age <= 3 else [76, 20, 4]
    return rng.choices(["NONE", "MINOR", "MAJOR"], weights=weights)[0]


def price_for(model, trim, year, km, condition, accident, owners):
    base = model["refPrice2022"] * (YEAR_STEP ** (year - REF_YEAR))
    base *= TRIM_MULTIPLIER[trim["tier"]]

    age = max(TODAY - year, 1)
    km_ratio = km / (age * EXPECTED_KM_PER_YEAR)
    base *= min(max(1 - 0.12 * (km_ratio - 1), 0.90), 1.08)

    base *= CONDITION_MULTIPLIER[condition]
    base *= ACCIDENT_MULTIPLIER[accident]
    base *= OWNER_MULTIPLIER[owners]
    return int(round(base / 5000.0) * 5000)


def airbags_for(model, trim, year):
    """Airbag count for a trim.

    Six airbags became standard fitment across most of this segment from 2023, so a base trim of
    a recent car is not the two-airbag car a base trim of a 2019 one was.
    """
    fitted = model["airbags"]
    if trim["tier"] == "top":
        return fitted
    if trim["tier"] == "mid":
        return min(fitted, 6)
    return min(fitted, 6) if year >= 2023 else min(fitted, 2)


def build(catalogue, rng):
    listings = []
    for model in catalogue:
        for _ in range(rng.choice([3, 4, 4, 5])):
            trim = rng.choice(model["trims"])
            # Fuel and gearbox are drawn as a pair, never independently: there is no CNG
            # automatic and no diesel AMT on most of this list, and picking them separately
            # invents cars that were never sold.
            powertrain = rng.choice(model["powertrains"])
            year = pick_year(rng, model)
            age = max(TODAY - year, 1)

            km = int(round(age * rng.uniform(*KM_PER_YEAR) / 500.0) * 500)
            owners = pick_owner(rng, age)
            condition = pick_condition(rng, age, km)
            accident = pick_accident(rng, age)

            listings.append(dict(
                make=model["make"], model=model["model"], variant=trim["name"],
                year=year, price=price_for(model, trim, year, km, condition, accident, owners),
                kilometres=km, fuel=powertrain[0], transmission=powertrain[1],
                body=model["body"],
                seats=model["seats"], owners=owners, safety=model["safety"],
                condition=condition, accident=accident,
                city=rng.choice(CITIES), colour=rng.choice(COLOURS),
                airbags=airbags_for(model, trim, year), features=trim["features"]))
    listings.sort(key=lambda l: (l["make"], l["model"], l["year"], l["variant"]))
    return listings


def quote(text):
    return "'" + text.replace("'", "''") + "'"


def render(listings):
    out = [
        "-- Seed data for the used-car catalogue. GENERATED FILE -- do not edit by hand.",
        "--",
        "-- Regenerate with:  python3 scripts/generate_seed.py",
        "--               or: ./mvnw -Pseed generate-resources",
        "--",
        "-- Source of truth is scripts/seed-data/catalogue.json, which holds one entry per model.",
        "-- Generation is deterministic, so a catalogue change shows up as a reviewable diff.",
        "--",
        f"-- {len(listings)} listings across {len({l['model'] for l in listings})} models. Prices are",
        "-- representative of the Indian used-car market, sanity-checked against public listings in",
        "-- September 2026. The catalogue is synthetic and no third-party data is included.",
        "",
        "INSERT INTO vehicles (id, make, model, variant, model_year, price, kilometres, fuel_type,",
        "                      transmission, body_type, seats, owner_count, safety_rating,",
        "                      condition_grade, accident_history, city, colour, airbags) VALUES",
    ]
    rows = []
    for i, l in enumerate(listings, start=1):
        rows.append("  (%d, %s, %s, %s, %d, %d.00, %d, %s, %s, %s, %d, %d, %d, %s, %s, %s, %s, %d)" % (
            i, quote(l["make"]), quote(l["model"]), quote(l["variant"]), l["year"], l["price"],
            l["kilometres"], quote(l["fuel"]), quote(l["transmission"]), quote(l["body"]),
            l["seats"], l["owners"], l["safety"], quote(l["condition"]), quote(l["accident"]),
            quote(l["city"]), quote(l["colour"]), l["airbags"]))
    out.append(",\n".join(rows) + ";")
    out += ["", "",
            "-- Feature tags. Open-ended by design: a new feature is a row here, not a schema change.",
            "-- A narrow tag always carries its broad tag, so a car with panoramic_sunroof also has",
            "-- sunroof and matches a plain search for one.",
            "INSERT INTO vehicle_features (vehicle_id, feature) VALUES"]
    blocks = []
    for i, l in enumerate(listings, start=1):
        head = f"  -- {i}: {l['make']} {l['model']} {l['variant']} ({l['year']})"
        body = ",\n".join("  (%d, %s)" % (i, quote(f)) for f in l["features"])
        blocks.append(head + "\n" + body)
    out.append(",\n".join(blocks) + ";")
    out.append("")
    return "\n".join(out)


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(root)
    catalogue = json.load(open(CATALOGUE))
    listings = build(catalogue, random.Random(RNG_SEED))
    open(OUT, "w").write(render(listings))

    tags = sum(len(l["features"]) for l in listings)
    print(f"wrote {OUT}: {len(listings)} listings, {tags} feature rows, "
          f"{len({l['model'] for l in listings})} models")


if __name__ == "__main__":
    main()
