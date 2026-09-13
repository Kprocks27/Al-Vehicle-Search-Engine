package com.cars24.carsearch.search;

/**
 * A feature tag the parser asked for that could not be honoured.
 *
 * <p>Separate from {@link DroppedFilter} rather than merged into one list of ignored things. The
 * request model keeps filters and features apart so the two can never be confused for each other;
 * the response keeps the same split for the same reason, and it lets each entry carry only the
 * fields that mean something for its kind.
 *
 * @param category ops signal only -- not serialised into the response.
 */
public record DroppedFeature(String tag, String reason, DropCategory category) {
}
