package com.cars24.carsearch.nlq.stub;

import com.cars24.carsearch.nlq.ParsedFilter;
import com.cars24.carsearch.nlq.ParsedQuery;
import com.cars24.carsearch.nlq.QueryNormalizer;
import com.cars24.carsearch.nlq.QueryNotUnderstoodException;
import com.cars24.carsearch.nlq.QueryParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fixture-backed parser: a lookup table of known sentences and the structure they mean.
 *
 * <p>This is scaffolding, and it is meant to be deleted the day the LLM implementation lands. It
 * exists so the rest of the pipeline -- validation, fuzzy resolution, query building, the
 * endpoint -- can be built, run and tested end to end without an API key, a network call, or a
 * bill. It is a table lookup, not a second parser: there is no regex matching, no keyword
 * extraction, no attempt at understanding. Anything cleverer here would be a competing
 * implementation of the one job the model is supposed to do, and would have to be maintained
 * alongside it.
 *
 * <p>Three of the fixtures return deliberately invalid structure. They are how the validation
 * boundary can be demonstrated by hand, without waiting for a real model to hallucinate: one is
 * partially usable and runs with the bad condition dropped, two are entirely unusable and leave
 * nothing to search.
 */
public class StubQueryParser implements QueryParser {

    private final Map<String, ParsedQuery> byPhrase = new LinkedHashMap<>();
    private final List<String> canonicalPhrases = new ArrayList<>();

    public StubQueryParser() {
        register(
                new ParsedQuery(
                        List.of(
                                filter("bodyType", "EQUALS", "SUV"),
                                filter("price", "UNDER", "1500000")),
                        List.of()),
                "Show SUVs under 15L",
                "SUVs under 15 lakh",
                "show suvs under Rs 15 lakhs",
                "suv below 15 lakhs");

        register(
                new ParsedQuery(
                        List.of(
                                filter("fuelType", "EQUALS", "DIESEL"),
                                filter("transmission", "EQUALS", "AUTOMATIC"),
                                filter("kilometres", "UNDER", "80000")),
                        List.of()),
                "Diesel automatic cars below 80k km",
                "diesel automatic under 80000 km",
                "automatic diesel cars with less than 80k kilometres");

        // "Family" is the model's leap: seven seats plus child-seat anchors. "High safety" stays
        // a level -- FuzzyBands turns it into >= 4 stars, not the model.
        register(
                new ParsedQuery(
                        List.of(
                                filter("seats", "OVER", "6"),
                                filter("safetyRating", "EQUALS", "HIGH")),
                        List.of("isofix")),
                "Family cars with high safety ratings",
                "family car with a high safety rating",
                "safe family cars");

        register(
                new ParsedQuery(
                        List.of(
                                filter("fuelType", "EQUALS", "PETROL"),
                                filter("bodyType", "EQUALS", "HATCHBACK")),
                        List.of("sunroof")),
                "Petrol hatchbacks with a sunroof",
                "petrol hatchback with sunroof");

        register(
                new ParsedQuery(
                        List.of(
                                filter("ownerCount", "EQUALS", "1"),
                                filter("price", "BETWEEN", "500000", "1000000")),
                        List.of()),
                "First owner cars between 5 and 10 lakh",
                "single owner cars from 5 to 10 lakhs");

        // Exercises a fuzzy level on a second field, and a text-valued filter.
        register(
                new ParsedQuery(
                        List.of(
                                filter("kilometres", "EQUALS", "LOW"),
                                filter("transmission", "EQUALS", "AUTOMATIC"),
                                filter("city", "EQUALS", "Bengaluru")),
                        List.of()),
                "Low kilometre automatic cars in Bengaluru",
                "automatic cars in bangalore with low kms");

        register(
                new ParsedQuery(
                        List.of(
                                filter("seats", "EQUALS", "7"),
                                filter("fuelType", "EQUALS", "DIESEL"),
                                filter("bodyType", "EQUALS", "SUV")),
                        List.of("camera_360", "cruise_control")),
                "Seven seater diesel SUVs with a 360 degree camera and cruise control");

        register(
                new ParsedQuery(
                        List.of(
                                filter("make", "EQUALS", "Tata"),
                                filter("safetyRating", "EQUALS", "HIGH"),
                                filter("accidentHistory", "EQUALS", "NONE")),
                        List.of()),
                "Accident free Tata cars with high safety ratings");

        // --- Fixtures that return invalid structure, to demonstrate the validation boundary. ---

        // The important one: a query that is mostly fine. "mileage" is not a column we hold, so
        // that one filter is dropped and the other three conditions still run. This is what the
        // shopper-facing behaviour looks like when a model half-understands a sentence.
        register(
                new ParsedQuery(
                        List.of(
                                filter("colour", "EQUALS", "Red"),
                                filter("bodyType", "EQUALS", "SUV"),
                                filter("mileage", "OVER", "20")),
                        List.of("panoramic_sunroof")),
                "Red SUVs with a panoramic sunroof and mileage over 20 kmpl");

        // An invented column and nothing else, so there is nothing left to search on.
        register(
                new ParsedQuery(
                        List.of(filter("mileage", "OVER", "20")),
                        List.of()),
                "Cars with mileage over 20 kmpl");

        // An invented feature tag, and a comparison outside the allowed four.
        register(
                new ParsedQuery(
                        List.of(filter("colour", "NOT_EQUALS", "White")),
                        List.of("teleport_mode")),
                "Any colour except white with teleport mode");
    }

    @Override
    public ParsedQuery parse(String query) {
        ParsedQuery fixture = byPhrase.get(QueryNormalizer.normalize(query));
        if (fixture == null) {
            throw new QueryNotUnderstoodException(
                    "The stub parser only recognises a fixed set of demo queries. "
                            + "Try one of: " + String.join(" | ", canonicalPhrases));
        }
        return fixture;
    }

    /** The phrasings a reviewer can type. Surfaced by the API so the demo is self-documenting. */
    public List<String> supportedQueries() {
        return List.copyOf(canonicalPhrases);
    }

    /**
     * @param phrasings the first is canonical and gets advertised; the rest are aliases, so a
     *                  reviewer typing the obvious variant still gets a result
     */
    private void register(ParsedQuery parsed, String... phrasings) {
        canonicalPhrases.add(phrasings[0]);
        for (String phrasing : phrasings) {
            byPhrase.put(QueryNormalizer.normalize(phrasing), parsed);
        }
    }

    private static ParsedFilter filter(String field, String comparison, String... values) {
        return new ParsedFilter(field, comparison, List.of(values));
    }
}
