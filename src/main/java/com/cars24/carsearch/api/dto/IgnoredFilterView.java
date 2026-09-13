package com.cars24.carsearch.api.dto;

import com.cars24.carsearch.search.DroppedFilter;

import java.util.List;

/** A filter that was asked for and not applied, with the reason it was not. */
public record IgnoredFilterView(String field, String comparison, List<String> values, String reason) {

    public static IgnoredFilterView from(DroppedFilter dropped) {
        return new IgnoredFilterView(
                dropped.field(), dropped.comparison(), dropped.values(), dropped.reason());
    }
}
