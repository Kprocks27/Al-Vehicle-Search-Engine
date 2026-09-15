package com.cars24.carsearch.search;

import java.util.List;
import java.util.Set;

/**
 * A validated, fully-typed search. Trusted.
 *
 * <p>The validator is the only code that produces a {@code SearchCriteria}: every field is a
 * known column, every comparison is one of the four, every value matches its field's type, and
 * every feature tag is in the catalogue. The query builder accepts this and nothing else, so no
 * existing path from raw parser output to the database skips validation. The compiler does not
 * enforce that -- this is a public record, and a new caller could construct one directly.
 *
 * <p>Filters and features are ANDed, and so are the individual entries within each. A shopper
 * listing three things wants all three.
 */
public record SearchCriteria(List<FieldCriterion> filters, Set<String> features) {

    public SearchCriteria {
        filters = List.copyOf(filters);
        features = Set.copyOf(features);
    }

    public boolean isEmpty() {
        return filters.isEmpty() && features.isEmpty();
    }
}
