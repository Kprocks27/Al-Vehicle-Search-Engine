package com.cars24.carsearch.api.dto;

import com.cars24.carsearch.search.DroppedFeature;

/** A feature tag that was asked for and not applied, with the reason it was not. */
public record IgnoredFeatureView(String tag, String reason) {

    public static IgnoredFeatureView from(DroppedFeature dropped) {
        return new IgnoredFeatureView(dropped.tag(), dropped.reason());
    }
}
