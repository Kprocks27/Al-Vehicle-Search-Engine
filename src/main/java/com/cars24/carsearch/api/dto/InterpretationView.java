package com.cars24.carsearch.api.dto;

import com.cars24.carsearch.search.Interpretation;

import java.util.List;
import java.util.Set;

/**
 * What the service understood the sentence to mean -- both halves of it.
 *
 * <p>{@code filters} and {@code features} are what ran. {@code ignoredFilters} and
 * {@code ignoredFeatures} are what was asked for and could not be honoured. The ignored lists
 * mirror the applied ones rather than being merged into a single "ignored" array, for the same
 * reason the request keeps filters and features apart: they are different kinds of thing, and a
 * merged list would either lose fields or carry nulls for half its entries.
 *
 * <p>All four are always present, empty when there is nothing to report. A client checking whether
 * anything was dropped should not also have to handle an absent key.
 */
public record InterpretationView(List<FilterView> filters,
                                 Set<String> features,
                                 List<IgnoredFilterView> ignoredFilters,
                                 List<IgnoredFeatureView> ignoredFeatures) {

    public static InterpretationView from(Interpretation interpretation) {
        return new InterpretationView(
                interpretation.criteria().filters().stream().map(FilterView::from).toList(),
                interpretation.criteria().features(),
                interpretation.ignoredFilters().stream().map(IgnoredFilterView::from).toList(),
                interpretation.ignoredFeatures().stream().map(IgnoredFeatureView::from).toList());
    }
}
