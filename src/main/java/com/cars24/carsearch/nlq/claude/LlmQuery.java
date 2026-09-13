package com.cars24.carsearch.nlq.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The model's response, exactly as the JSON schema shapes it.
 *
 * <p>Separate from {@code ParsedQuery} on purpose. This type mirrors the wire schema; converting it
 * is the one place the two shapes are reconciled, so a schema change cannot silently alter what the
 * rest of the pipeline sees.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmQuery(List<LlmFilter> filters, List<String> features) {

    public LlmQuery {
        filters = filters == null ? List.of() : List.copyOf(filters);
        features = features == null ? List.of() : List.copyOf(features);
    }
}
