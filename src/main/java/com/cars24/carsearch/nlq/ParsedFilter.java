package com.cars24.carsearch.nlq;

import java.util.List;

/**
 * One condition as the parser produced it. Untrusted.
 *
 * <p>Every member is a String, including the comparison, and that is deliberate. If this record
 * declared {@code Comparison comparison}, an unknown comparison would blow up inside Jackson
 * during deserialisation -- somewhere unhelpful, with a message shaped like a serialisation bug
 * rather than a model-output problem, and untestable without a real LLM. Keeping the wire shape
 * stringly-typed means every rejection happens in one place, {@code ParsedQueryValidator}, where
 * it can be tested by hand and reported properly.
 *
 * <p>{@code values} is a list rather than a single value so BETWEEN does not need a second,
 * usually-null field. The arity of each comparison is checked during validation.
 *
 * @param field      a name the parser believes is a vehicle column
 * @param comparison one of UNDER, OVER, EQUALS, BETWEEN -- claimed, not verified
 * @param values     one value, or two for BETWEEN; either literals or a fuzzy level like "HIGH"
 */
public record ParsedFilter(String field, String comparison, List<String> values) {

    public ParsedFilter {
        // A model that omits a key gives us null, not an empty list. Normalise at the door so
        // nothing downstream has to null-check a collection.
        values = values == null ? List.of() : List.copyOf(values);
    }
}
