package com.cars24.carsearch.search;

import java.util.List;

/**
 * Everything the validator concluded about a query: what will be searched, and what was thrown
 * away getting there.
 *
 * <p>Kept separate from {@link SearchCriteria} deliberately. {@code SearchCriteria} is the thing
 * that becomes SQL and stays strictly "what runs" -- the query builder must not be handed a bag
 * that also contains conditions we decided not to honour. The dropped lists ride alongside it,
 * inert, for the response and the log.
 */
public record Interpretation(SearchCriteria criteria,
                             List<DroppedFilter> ignoredFilters,
                             List<DroppedFeature> ignoredFeatures) {

    public Interpretation {
        ignoredFilters = List.copyOf(ignoredFilters);
        ignoredFeatures = List.copyOf(ignoredFeatures);
    }

    public boolean droppedAnything() {
        return !ignoredFilters.isEmpty() || !ignoredFeatures.isEmpty();
    }

    public int droppedCount() {
        return ignoredFilters.size() + ignoredFeatures.size();
    }
}
