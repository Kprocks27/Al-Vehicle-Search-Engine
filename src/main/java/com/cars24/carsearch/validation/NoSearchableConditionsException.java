package com.cars24.carsearch.validation;

import com.cars24.carsearch.nlq.QueryNotUnderstoodException;
import com.cars24.carsearch.search.DroppedFeature;
import com.cars24.carsearch.search.DroppedFilter;

import java.util.List;

/**
 * Nothing searchable survived validation.
 *
 * <p>Either the parser returned no conditions at all, or every condition it returned had to be
 * dropped. Running the query anyway would mean no WHERE clause and the entire catalogue coming
 * back, which is the failure that looks most like success.
 *
 * <p>Extends {@link QueryNotUnderstoodException} because that is what it is from the shopper's
 * side -- not enough of the sentence landed -- so it inherits the 422. It carries the dropped
 * lists so the response can still say what was thrown away: a query that fails outright must
 * explain itself just as clearly as one that partially succeeds, or the drops are silent in
 * exactly the case where the shopper most needs to know.
 */
public class NoSearchableConditionsException extends QueryNotUnderstoodException {

    private final transient List<DroppedFilter> ignoredFilters;
    private final transient List<DroppedFeature> ignoredFeatures;

    public NoSearchableConditionsException(List<DroppedFilter> ignoredFilters,
                                           List<DroppedFeature> ignoredFeatures) {
        super(message(ignoredFilters, ignoredFeatures));
        this.ignoredFilters = List.copyOf(ignoredFilters);
        this.ignoredFeatures = List.copyOf(ignoredFeatures);
    }

    private static String message(List<DroppedFilter> filters, List<DroppedFeature> features) {
        int dropped = filters.size() + features.size();
        return dropped == 0
                ? "No filters or features could be derived from that query."
                : "None of the " + dropped + " condition(s) in that query could be used.";
    }

    public List<DroppedFilter> ignoredFilters() {
        return ignoredFilters;
    }

    public List<DroppedFeature> ignoredFeatures() {
        return ignoredFeatures;
    }
}
