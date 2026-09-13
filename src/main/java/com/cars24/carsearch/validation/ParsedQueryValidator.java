package com.cars24.carsearch.validation;

import com.cars24.carsearch.nlq.ParsedFilter;
import com.cars24.carsearch.nlq.ParsedQuery;
import com.cars24.carsearch.search.DropCategory;
import com.cars24.carsearch.search.DroppedFeature;
import com.cars24.carsearch.search.DroppedFilter;
import com.cars24.carsearch.search.FieldCriterion;
import com.cars24.carsearch.search.Interpretation;
import com.cars24.carsearch.search.SearchCriteria;
import com.cars24.carsearch.vocabulary.Comparison;
import com.cars24.carsearch.vocabulary.FeatureCatalogue;
import com.cars24.carsearch.vocabulary.FuzzyBands;
import com.cars24.carsearch.vocabulary.FuzzyLevel;
import com.cars24.carsearch.vocabulary.VehicleField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The boundary. Untrusted parser output goes in, an {@link Interpretation} comes out.
 *
 * <p>Everything the parser said is treated as a claim to be checked: that the field is a column,
 * that the comparison is one of the four, that the field supports it, that the number of values
 * matches, that each value is the right type, that a range runs the right way, and that every
 * feature tag is one we stock.
 *
 * <p>A claim that fails is dropped, not fatal. One unrecognised condition used to fail the whole
 * query, which meant a shopper asking for four things and getting three of them right was shown
 * nothing. Now the parts that cannot be honoured are set aside and the query runs on what remains
 * -- and every dropped part comes back in the response, because a filter that vanishes silently is
 * worse than one that fails loudly. Only two things are still fatal: output so large it means the
 * parser has come off the rails, and output that leaves nothing at all to search.
 */
@Component
public class ParsedQueryValidator {

    /**
     * Sanity ceilings. These stay fatal, unlike everything else here. A well-behaved parser
     * produces a handful of conditions; two hundred is not a query with some bad parts to drop,
     * it is a parser that has stopped working, and there is no sensible subset to honour.
     */
    private static final int MAX_FILTERS = 20;
    private static final int MAX_FEATURES = 20;

    private final FeatureCatalogue featureCatalogue;

    public ParsedQueryValidator(FeatureCatalogue featureCatalogue) {
        this.featureCatalogue = featureCatalogue;
    }

    public Interpretation validate(ParsedQuery parsed) {
        enforceCeilings(parsed);

        List<FieldCriterion> criteria = new ArrayList<>();
        List<DroppedFilter> ignoredFilters = new ArrayList<>();
        for (ParsedFilter filter : parsed.filters()) {
            try {
                criteria.add(toCriterion(filter));
            } catch (UnusableFilterException e) {
                ignoredFilters.add(DroppedFilter.of(filter, e.getMessage(), e.category()));
            }
        }

        Set<String> features = new LinkedHashSet<>();
        List<DroppedFeature> ignoredFeatures = new ArrayList<>();
        for (String tag : parsed.features()) {
            if (tag == null || tag.isBlank()) {
                // Malformed output, not a request we cannot serve: nothing a shopper types
                // produces an empty tag.
                ignoredFeatures.add(new DroppedFeature(tag, "feature tag is blank", DropCategory.PARSER_DRIFT));
            } else if (!featureCatalogue.contains(tag)) {
                ignoredFeatures.add(new DroppedFeature(tag, "unknown feature tag", DropCategory.UNSUPPORTED_REQUEST));
            } else {
                features.add(featureCatalogue.canonicalise(tag));
            }
        }

        // Dropping is tolerant, but tolerance has to stop short of a query with no WHERE clause:
        // that would hand back the entire catalogue and look like a working search.
        if (criteria.isEmpty() && features.isEmpty()) {
            throw new NoSearchableConditionsException(ignoredFilters, ignoredFeatures);
        }

        return new Interpretation(new SearchCriteria(criteria, features), ignoredFilters, ignoredFeatures);
    }

    private void enforceCeilings(ParsedQuery parsed) {
        List<Violation> violations = new ArrayList<>();
        if (parsed.filters().size() > MAX_FILTERS) {
            violations.add(new Violation("filters",
                    "too many filters: " + parsed.filters().size() + ", limit is " + MAX_FILTERS));
        }
        if (parsed.features().size() > MAX_FEATURES) {
            violations.add(new Violation("features",
                    "too many features: " + parsed.features().size() + ", limit is " + MAX_FEATURES));
        }
        if (!violations.isEmpty()) {
            throw new InvalidParsedQueryException(violations);
        }
    }

    /**
     * Builds one criterion, or throws with the reason it cannot be built.
     *
     * <p>Reads top to bottom as the sequence of claims being checked. The alternative -- threading
     * an Optional and a violations list through every step -- buried that sequence in plumbing.
     */
    private FieldCriterion toCriterion(ParsedFilter filter) {
        // An unknown field is how the parser is instructed to surface anything the catalogue
        // cannot express -- a property we do not store, or a constraint needing a comparison we
        // do not support. So this is the shopper asking for something, not the model misbehaving.
        VehicleField field = VehicleField.fromWireName(filter.field())
                .orElseThrow(() -> unsupported("unknown field '" + filter.field() + "'"));

        Comparison comparison = parseComparison(filter.comparison())
                .orElseThrow(() -> drift("unknown comparison '" + filter.comparison()
                        + "'; allowed are UNDER, OVER, EQUALS, BETWEEN"));

        if (!field.supports(comparison)) {
            throw drift(comparison + " needs an ordered field, but '" + field.wireName() + "' is not");
        }
        if (filter.values().size() != comparison.arity()) {
            throw drift(comparison + " takes " + comparison.arity() + " value(s), got "
                    + filter.values().size());
        }

        // A magnitude short-circuits coercion entirely: "HIGH" is not a malformed number, it is a
        // different kind of value, and reporting it as the former would send whoever is tuning the
        // prompt in the wrong direction.
        if (looksFuzzy(field, filter)) {
            return resolveFuzzy(field, comparison, filter);
        }

        List<Object> coerced = new ArrayList<>(filter.values().size());
        for (String raw : filter.values()) {
            coerced.add(coerce(field, raw));
        }

        if (comparison == Comparison.BETWEEN && outOfOrder(coerced.get(0), coerced.get(1))) {
            throw drift("BETWEEN lower bound " + coerced.get(0) + " is above upper bound "
                    + coerced.get(1));
        }

        return new FieldCriterion(field, comparison, coerced);
    }

    /**
     * Turns a magnitude like HIGH into a real comparison.
     *
     * <p>A level always arrives with EQUALS -- "safetyRating EQUALS HIGH", read as "safety rating
     * is high". The band supplies the real comparison, so "safetyRating UNDER HIGH" is a
     * contradiction between two sources of truth and is dropped rather than guessed at.
     */
    private FieldCriterion resolveFuzzy(VehicleField field, Comparison comparison, ParsedFilter filter) {
        FuzzyLevel level = FuzzyLevel.fromToken(filter.values().get(0)).orElseThrow();

        if (comparison != Comparison.EQUALS) {
            throw drift("magnitude '" + level + "' must be paired with EQUALS, got " + comparison);
        }
        if (!FuzzyBands.hasBands(field)) {
            throw drift("'" + field.wireName()
                    + "' has no magnitude bands; an explicit value is required");
        }

        FuzzyBands.Band band = FuzzyBands.resolve(field, level)
                // The field has bands, just not this level -- kilometres defines LOW and HIGH but
                // no MEDIUM. Snapping to the nearest band would answer a different question.
                .orElseThrow(() -> drift("'" + field.wireName() + "' has no " + level
                        + " band; defined levels are " + FuzzyBands.levelsFor(field)));

        return new FieldCriterion(field, band.comparison(), band.valueList());
    }

    /**
     * A single value on an ordered field that spells a level. Text and enum fields are excluded:
     * on those, "LOW" is a literal, and a colour called Low would otherwise be unsearchable.
     */
    private boolean looksFuzzy(VehicleField field, ParsedFilter filter) {
        return field.valueType().isOrdered()
                && filter.values().size() == 1
                && FuzzyLevel.fromToken(filter.values().get(0)).isPresent();
    }

    private Object coerce(VehicleField field, String raw) {
        if (raw == null || raw.isBlank()) {
            throw drift("value for '" + field.wireName() + "' is blank");
        }
        String value = raw.trim();

        return switch (field.valueType()) {
            case TEXT -> value;
            case ENUM -> coerceEnum(field, value);
            case INTEGER -> coerceInteger(field, value);
            case MONEY -> coerceMoney(field, value);
        };
    }

    private Object coerceEnum(VehicleField field, String value) {
        for (Enum<?> constant : field.enumType().getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        throw drift("'" + value + "' is not a valid " + field.wireName()
                + "; allowed are " + allowedConstants(field));
    }

    private Object coerceInteger(VehicleField field, String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw drift("'" + field.wireName() + "' cannot be negative, got " + parsed);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw drift("'" + value + "' is not a whole number for '" + field.wireName() + "'");
        }
    }

    private Object coerceMoney(VehicleField field, String value) {
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.signum() < 0) {
                throw drift("'" + field.wireName() + "' cannot be negative, got "
                        + parsed.toPlainString());
            }
            return parsed;
        } catch (NumberFormatException e) {
            // Rupee symbols, commas and "15L" are the model's to expand. If they reach here the
            // prompt needs fixing, so say exactly what arrived rather than trying to salvage it.
            throw drift("'" + value + "' is not a plain rupee amount for '" + field.wireName() + "'");
        }
    }

    private static Optional<Comparison> parseComparison(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(Comparison.values())
                .filter(c -> c.name().equals(raw.trim().toUpperCase(Locale.ROOT)))
                .findFirst();
    }

    /** Only ordered fields reach BETWEEN, and both ordered value types are Numbers. */
    private static boolean outOfOrder(Object lower, Object upper) {
        return ((Number) lower).doubleValue() > ((Number) upper).doubleValue();
    }

    private static String allowedConstants(VehicleField field) {
        return Arrays.stream(field.enumType().getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private static UnusableFilterException unsupported(String reason) {
        return new UnusableFilterException(reason, DropCategory.UNSUPPORTED_REQUEST);
    }

    private static UnusableFilterException drift(String reason) {
        return new UnusableFilterException(reason, DropCategory.PARSER_DRIFT);
    }

    /**
     * Internal control flow: aborts one filter and carries the reason it was dropped. Private and
     * caught within this class, so it never escapes as an exception -- it becomes a
     * {@link DroppedFilter} at exactly one place. No stack trace, because the reason string is the
     * entire payload and dropping a filter is an expected outcome, not an error.
     */
    private static class UnusableFilterException extends RuntimeException {

        private final transient DropCategory category;

        UnusableFilterException(String reason, DropCategory category) {
            super(reason);
            this.category = category;
        }

        DropCategory category() {
            return category;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
