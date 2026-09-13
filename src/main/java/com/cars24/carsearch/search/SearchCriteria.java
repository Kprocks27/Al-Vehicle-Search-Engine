package com.cars24.carsearch.search;

import java.util.List;
import java.util.Set;

/**
 * A validated, fully-typed search. Trusted.
 *
 * <p>Anything constructed as a {@code SearchCriteria} has already been through the validator:
 * every field is a known column, every comparison is one of the four, every value matches its
 * field's type, and every feature tag is in the catalogue. This type existing separately from
 * {@code ParsedQuery} is what makes "validated" a thing the compiler can check -- the query
 * builder accepts this and nothing else, so there is no code path from raw parser output to the
 * database that skips validation.
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
