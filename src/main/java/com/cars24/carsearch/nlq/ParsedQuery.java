package com.cars24.carsearch.nlq;

import java.util.List;

/**
 * The parser's entire output: what the sentence asked for, as structure. Untrusted.
 *
 * <p>Two lists, not one. Filters name a column; features name a tag. Merging them into a single
 * "conditions" list would mean the model decides which kind each condition is, and the failure
 * mode is quiet -- {@code sunroof = true} as a column filter is a plausible-looking object that
 * only fails at query time, or worse, matches nothing and looks like empty stock. Splitting them
 * makes the two kinds unmixable by construction: the model picks a list, and the list it picked
 * determines how the value is validated and how it is turned into a predicate.
 *
 * <p>This type never reaches the database layer. It is validated into
 * {@code com.cars24.carsearch.search.SearchCriteria} first.
 *
 * @param filters  column conditions
 * @param features feature tags the vehicle must carry, all of them
 */
public record ParsedQuery(List<ParsedFilter> filters, List<String> features) {

    public ParsedQuery {
        filters = filters == null ? List.of() : List.copyOf(filters);
        features = features == null ? List.of() : List.copyOf(features);
    }

    public static ParsedQuery empty() {
        return new ParsedQuery(List.of(), List.of());
    }

    public boolean isEmpty() {
        return filters.isEmpty() && features.isEmpty();
    }
}
