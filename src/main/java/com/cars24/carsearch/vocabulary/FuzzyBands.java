package com.cars24.carsearch.vocabulary;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where vague words become numbers.
 *
 * <p>The parser may return {@code HIGH} for a field instead of a value. This table is the only
 * place that decides what HIGH means. Two reasons it lives here and not in the prompt:
 *
 * <ol>
 *   <li>Determinism. The same sentence produces the same SQL on every call, and a threshold
 *       change is a reviewable diff rather than a prompt tweak nobody can audit.
 *   <li>Domain ownership. "High safety" is not a language question. Global NCAP results are read
 *       in India as 4-5 stars good to excellent, 3 acceptable, 2 and below basic -- so HIGH is
 *       4 and up. A model has no business picking that number.
 * </ol>
 *
 * <p>Only fields with a defensible, segment-independent scale get bands. Price deliberately has
 * none: "cheap" means under 5 lakh for a hatchback and under 15 for a seven-seater, so a single
 * threshold would be wrong for most of the catalogue. The model is expected to resolve budget
 * words into an actual rupee figure from context instead, and if it cannot, no filter is better
 * than a fabricated one.
 */
public final class FuzzyBands {

    private static final Map<VehicleField, Map<FuzzyLevel, Band>> BANDS = new EnumMap<>(VehicleField.class);

    static {
        // Global NCAP stars, 0-5.
        BANDS.put(VehicleField.SAFETY_RATING, Map.of(
                FuzzyLevel.HIGH, new Band(Comparison.OVER, 4),
                FuzzyLevel.MEDIUM, new Band(Comparison.EQUALS, 3),
                FuzzyLevel.LOW, new Band(Comparison.UNDER, 2)));

        // Odometer bands, aligned to the valuation brackets used on the buying side: under 30k is
        // priced as low-usage stock, over 100k as high-usage.
        //
        // Two bands, not three. Nobody asks for a "medium mileage" car -- it is not a phrase
        // buyers use -- and the range between the two bands is already expressible with UNDER and
        // OVER carrying a real number, so a MEDIUM band would add a threshold nobody asked for.
        BANDS.put(VehicleField.KILOMETRES, Map.of(
                FuzzyLevel.LOW, new Band(Comparison.UNDER, 30_000),
                FuzzyLevel.HIGH, new Band(Comparison.OVER, 100_000)));
    }

    private FuzzyBands() {
    }

    /**
     * Resolves a level for a field, or empty if the field has no bands at all or none for this
     * particular level -- both are rejections, not fallbacks. A level the field does not define
     * means the parser invented a scale, and guessing at the nearest band would silently answer a
     * different question to the one asked.
     */
    public static Optional<Band> resolve(VehicleField field, FuzzyLevel level) {
        return Optional.ofNullable(BANDS.get(field)).map(byLevel -> byLevel.get(level));
    }

    public static boolean hasBands(VehicleField field) {
        return BANDS.containsKey(field);
    }

    /** The levels a field actually defines, for error messages and for the model's prompt. */
    public static String levelsFor(VehicleField field) {
        return BANDS.getOrDefault(field, Map.of()).keySet().stream()
                .sorted()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /** A resolved band: the concrete comparison and value(s) a level stands for. */
    public record Band(Comparison comparison, Object... values) {

        public java.util.List<Object> valueList() {
            return java.util.List.of(values);
        }
    }
}
