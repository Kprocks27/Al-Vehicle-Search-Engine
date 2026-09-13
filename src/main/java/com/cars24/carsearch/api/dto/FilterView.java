package com.cars24.carsearch.api.dto;

import com.cars24.carsearch.search.FieldCriterion;

import java.math.BigDecimal;
import java.util.List;

/**
 * One condition, as it was actually applied.
 *
 * <p>This renders the criterion after validation and after fuzzy resolution, not what the parser
 * said. So "high safety rating" comes back as {@code safetyRating OVER 4} -- the shopper, and
 * anyone reading a bug report, can see the threshold that ran rather than the word that was typed.
 */
public record FilterView(String field, String comparison, List<String> values) {

    public static FilterView from(FieldCriterion criterion) {
        return new FilterView(
                criterion.field().wireName(),
                criterion.comparison().name(),
                criterion.values().stream().map(FilterView::render).toList());
    }

    private static String render(Object value) {
        // Rupee amounts print as plain digits; BigDecimal.toString would give scientific
        // notation for large values, which is not something to hand a front end.
        return value instanceof BigDecimal amount ? amount.toPlainString() : String.valueOf(value);
    }
}
