package com.cars24.carsearch.search;

import com.cars24.carsearch.nlq.ParsedFilter;

import java.util.List;

/**
 * A filter the parser asked for that could not be honoured, echoed back exactly as it arrived.
 *
 * <p>Raw strings, not typed values, because a dropped filter is by definition one we could not
 * type -- the field may not exist, the comparison may not suit it, the value may not be a number.
 * What the shopper gets to see is what was asked for and why it did not happen.
 *
 * @param reason   terse and self-explanatory. The long-form diagnostics go to the log, not into a
 *                 response a shopper reads.
 * @param category whose problem it is. Ops signal only -- not serialised into the response.
 */
public record DroppedFilter(String field,
                            String comparison,
                            List<String> values,
                            String reason,
                            DropCategory category) {

    public DroppedFilter {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public static DroppedFilter of(ParsedFilter filter, String reason, DropCategory category) {
        return new DroppedFilter(filter.field(), filter.comparison(), filter.values(), reason, category);
    }
}
