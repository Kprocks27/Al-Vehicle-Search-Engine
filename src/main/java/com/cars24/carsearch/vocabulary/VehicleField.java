package com.cars24.carsearch.vocabulary;

import com.cars24.carsearch.catalogue.AccidentHistory;
import com.cars24.carsearch.catalogue.BodyType;
import com.cars24.carsearch.catalogue.ConditionGrade;
import com.cars24.carsearch.catalogue.FuelType;
import com.cars24.carsearch.catalogue.Transmission;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The searchable columns, and the only field names a parser may emit.
 *
 * <p>This enum is the allow-list. If a name is not a constant here, the filter referencing it
 * never reaches the query builder. That is the whole reason field names are an enum and not
 * strings threaded through the pipeline: an unknown column becomes impossible to represent
 * a layer before it could become a SQL identifier.
 *
 * <p>Each constant carries three things: the name the parser uses on the wire, the JPA attribute
 * to resolve against {@code Vehicle}, and the value type. Keeping the wire name and the attribute
 * name as separate strings costs nothing today (they happen to match) and means renaming a column
 * later does not silently break the parser contract.
 *
 * <p>Note what is absent: {@code variant} and {@code id}. Both exist on the entity, neither is
 * searchable. Being a column is not enough to be a filter target.
 */
public enum VehicleField {

    MAKE("make", "make", ValueType.TEXT, null),
    MODEL("model", "model", ValueType.TEXT, null),
    YEAR("year", "year", ValueType.INTEGER, null),
    PRICE("price", "price", ValueType.MONEY, null),
    KILOMETRES("kilometres", "kilometres", ValueType.INTEGER, null),
    FUEL_TYPE("fuelType", "fuelType", ValueType.ENUM, FuelType.class),
    TRANSMISSION("transmission", "transmission", ValueType.ENUM, Transmission.class),
    BODY_TYPE("bodyType", "bodyType", ValueType.ENUM, BodyType.class),
    SEATS("seats", "seats", ValueType.INTEGER, null),
    OWNER_COUNT("ownerCount", "ownerCount", ValueType.INTEGER, null),
    SAFETY_RATING("safetyRating", "safetyRating", ValueType.INTEGER, null),
    CONDITION_GRADE("conditionGrade", "conditionGrade", ValueType.ENUM, ConditionGrade.class),
    ACCIDENT_HISTORY("accidentHistory", "accidentHistory", ValueType.ENUM, AccidentHistory.class),
    CITY("city", "city", ValueType.TEXT, null),
    COLOUR("colour", "colour", ValueType.TEXT, null),
    AIRBAGS("airbags", "airbags", ValueType.INTEGER, null);

    private static final Map<String, VehicleField> BY_WIRE_NAME =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            f -> f.wireName.toLowerCase(Locale.ROOT), Function.identity()));

    private final String wireName;
    private final String attribute;
    private final ValueType valueType;
    private final Class<? extends Enum<?>> enumType;

    VehicleField(String wireName, String attribute, ValueType valueType, Class<? extends Enum<?>> enumType) {
        this.wireName = wireName;
        this.attribute = attribute;
        this.valueType = valueType;
        this.enumType = enumType;
    }

    /**
     * Resolves a parser-supplied field name. Case-insensitive: the model is inconsistent about
     * casing and that is not worth failing a query over. It is not fuzzy beyond that -- "cost"
     * does not resolve to price. Synonyms are the model's job; this is the boundary check.
     */
    public static Optional<VehicleField> fromWireName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_WIRE_NAME.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    /** Every field name the parser is allowed to use. Also what we hand the model in its prompt. */
    public static String allWireNames() {
        return Arrays.stream(values()).map(VehicleField::wireName).collect(Collectors.joining(", "));
    }

    public String wireName() {
        return wireName;
    }

    /** JPA attribute name on {@code Vehicle}; resolved via the Criteria API, never concatenated. */
    public String attribute() {
        return attribute;
    }

    public ValueType valueType() {
        return valueType;
    }

    public Class<? extends Enum<?>> enumType() {
        return enumType;
    }

    public boolean supports(Comparison comparison) {
        return !comparison.requiresOrdering() || valueType.isOrdered();
    }
}
