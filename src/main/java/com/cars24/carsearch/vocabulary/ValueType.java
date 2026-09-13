package com.cars24.carsearch.vocabulary;

/**
 * How a field's values are parsed and compared.
 *
 * <p>The {@code ordered} flag is what decides which comparisons a field accepts. Asking for
 * "colour UNDER white" is nonsense, and this is where that gets rejected rather than being
 * discovered later as a ClassCastException inside the query builder.
 */
public enum ValueType {

    TEXT(false),
    ENUM(false),
    INTEGER(true),
    MONEY(true);

    private final boolean ordered;

    ValueType(boolean ordered) {
        this.ordered = ordered;
    }

    public boolean isOrdered() {
        return ordered;
    }
}
