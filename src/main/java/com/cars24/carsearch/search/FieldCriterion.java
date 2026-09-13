package com.cars24.carsearch.search;

import com.cars24.carsearch.vocabulary.Comparison;
import com.cars24.carsearch.vocabulary.VehicleField;

import java.util.List;

/**
 * One validated column condition, ready to become a predicate.
 *
 * <p>The difference between this and {@code ParsedFilter} is the whole point of the validation
 * layer. There, the field was a String that might name a column. Here it is an enum constant that
 * definitely does. There, the value was a String. Here it is an Integer, BigDecimal, String or
 * enum constant that has already been checked against the field's type. Nothing downstream needs
 * to re-check anything.
 *
 * <p>{@code values} is typed as Object because a criterion is deliberately heterogeneous across
 * fields. The invariant -- the runtime type matches {@link VehicleField#valueType()} -- is
 * established by the validator and consumed by the specification builder, which is the only
 * other class that touches these values.
 */
public record FieldCriterion(VehicleField field, Comparison comparison, List<Object> values) {

    public FieldCriterion {
        values = List.copyOf(values);
    }

    /** The single value, for UNDER, OVER and EQUALS. */
    public Object single() {
        return values.get(0);
    }

    public Object lower() {
        return values.get(0);
    }

    public Object upper() {
        return values.get(1);
    }
}
