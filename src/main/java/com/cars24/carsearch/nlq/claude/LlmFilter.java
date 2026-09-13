package com.cars24.carsearch.nlq.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One filter as the schema defines it. Every member is a string, including the comparison.
 *
 * <p>The schema constrains {@code comparison} to the four names, so in practice it arrives valid.
 * It is still carried as a String and still re-checked by the validator: structured outputs are a
 * strong constraint, not a guarantee we are willing to bet the database on, and the stub parser can
 * produce values the schema never would.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmFilter(String field, String comparison, List<String> values) {

    public LlmFilter {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
