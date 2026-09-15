package com.cars24.carsearch.validation;

import com.cars24.carsearch.catalogue.AccidentHistory;
import com.cars24.carsearch.catalogue.FuelType;
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
import com.cars24.carsearch.vocabulary.VehicleField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The validator is where untrusted model output either becomes a query or does not.
 *
 * <p>Most of these are about what gets <em>dropped</em>, since every case here is something a real
 * model does: inventing a column, inventing a feature, using a comparison outside the contract,
 * putting a word where a number belongs. A dropped condition must never be silent, so each test
 * checks both halves -- that the bad condition did not run, and that it came back reported.
 */
class ParsedQueryValidatorTest {

    /** A condition that always survives, so tests about dropping are not also tests about emptiness. */
    private static final ParsedFilter VALID = filter("bodyType", "EQUALS", "SUV");

    private ParsedQueryValidator validator;

    @BeforeEach
    void setUp() {
        FeatureCatalogue catalogue = new FeatureCatalogue();
        catalogue.setFeatures(List.of("sunroof", "isofix", "camera_360"));
        validator = new ParsedQueryValidator(catalogue);
    }

    @Nested
    @DisplayName("accepts well-formed output")
    class Accepts {

        @Test
        void typesValuesAccordingToTheField() {
            SearchCriteria criteria = validator.validate(new ParsedQuery(
                    List.of(
                            filter("bodyType", "EQUALS", "SUV"),
                            filter("price", "UNDER", "1500000"),
                            filter("make", "EQUALS", "Tata")),
                    List.of())).criteria();

            assertThat(criteria.filters()).hasSize(3);
            assertThat(criteria.filters().get(0).single()).isInstanceOf(Enum.class);
            assertThat(criteria.filters().get(1).single()).isEqualTo(new BigDecimal("1500000"));
            assertThat(criteria.filters().get(2).single()).isEqualTo("Tata");
        }

        @Test
        void canonicalisesFeatureTags() {
            SearchCriteria criteria = validator.validate(new ParsedQuery(
                    List.of(), List.of("Sunroof", "camera 360"))).criteria();

            assertThat(criteria.features()).containsExactlyInAnyOrder("sunroof", "camera_360");
        }

        @Test
        void keepsBothBoundsOfARange() {
            SearchCriteria criteria = validator.validate(new ParsedQuery(
                    List.of(filter("price", "BETWEEN", "500000", "1000000")), List.of())).criteria();

            FieldCriterion criterion = criteria.filters().get(0);
            assertThat(criterion.comparison()).isEqualTo(Comparison.BETWEEN);
            assertThat(criterion.lower()).isEqualTo(new BigDecimal("500000"));
            assertThat(criterion.upper()).isEqualTo(new BigDecimal("1000000"));
        }

        @Test
        void keepsBothBoundsOfAYearRange() {
            // "2018 to 2021 models". Year is a whole number rather than money, so it goes through
            // different coercion from the price range above.
            SearchCriteria criteria = validator.validate(new ParsedQuery(
                    List.of(filter("year", "BETWEEN", "2018", "2021")), List.of())).criteria();

            FieldCriterion criterion = criteria.filters().get(0);
            assertThat(criterion.field()).isEqualTo(VehicleField.YEAR);
            assertThat(criterion.comparison()).isEqualTo(Comparison.BETWEEN);
            assertThat(criterion.lower()).isEqualTo(2018);
            assertThat(criterion.upper()).isEqualTo(2021);
        }

        @Test
        void keepsBothBoundsOfAKilometreRange() {
            // "40k-60k km". Kilometres also has magnitude bands, so this checks two real numbers
            // are still read as a range and not mistaken for a level.
            SearchCriteria criteria = validator.validate(new ParsedQuery(
                    List.of(filter("kilometres", "BETWEEN", "40000", "60000")), List.of())).criteria();

            FieldCriterion criterion = criteria.filters().get(0);
            assertThat(criterion.comparison()).isEqualTo(Comparison.BETWEEN);
            assertThat(criterion.lower()).isEqualTo(40_000);
            assertThat(criterion.upper()).isEqualTo(60_000);
        }

        @Test
        void keepsEachRangeWithItsOwnField() {
            // "2019 to 2022, 6 to 9 lakhs, 20k to 50k km": three ranges in one sentence, and each
            // pair of bounds has to stay with the field it belongs to.
            SearchCriteria criteria = validator.validate(new ParsedQuery(
                    List.of(
                            filter("year", "BETWEEN", "2019", "2022"),
                            filter("price", "BETWEEN", "600000", "900000"),
                            filter("kilometres", "BETWEEN", "20000", "50000")),
                    List.of())).criteria();

            assertThat(criteria.filters())
                    .extracting(FieldCriterion::field, FieldCriterion::lower, FieldCriterion::upper)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(VehicleField.YEAR, 2019, 2022),
                            org.assertj.core.groups.Tuple.tuple(VehicleField.PRICE,
                                    new BigDecimal("600000"), new BigDecimal("900000")),
                            org.assertj.core.groups.Tuple.tuple(VehicleField.KILOMETRES, 20_000, 50_000));
        }

        @Test
        void readsNoAccidentHistoryAsAValue() {
            // Negative wording, but for a value the catalogue holds -- so a normal filter, not an
            // exclusion to be ignored.
            SearchCriteria criteria = validator.validate(new ParsedQuery(
                    List.of(filter("accidentHistory", "EQUALS", "NONE")), List.of())).criteria();

            assertThat(criteria.filters()).hasSize(1);
            assertThat(criteria.filters().get(0).single()).isEqualTo(AccidentHistory.NONE);
        }

        @Test
        void acceptsElectricAsAFuelType() {
            SearchCriteria criteria = validator.validate(new ParsedQuery(
                    List.of(filter("fuelType", "EQUALS", "ELECTRIC")), List.of())).criteria();

            assertThat(criteria.filters()).hasSize(1);
            assertThat(criteria.filters().get(0).single()).isEqualTo(FuelType.ELECTRIC);
        }

        @Test
        void reportsNothingIgnoredWhenEverythingIsUsable() {
            Interpretation interpretation = validator.validate(new ParsedQuery(
                    List.of(VALID), List.of("sunroof")));

            assertThat(interpretation.droppedAnything()).isFalse();
            assertThat(interpretation.ignoredFilters()).isEmpty();
            assertThat(interpretation.ignoredFeatures()).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolves magnitudes to thresholds this code owns")
    class Fuzzy {

        @Test
        void highSafetyRatingBecomesFourStarsAndUp() {
            FieldCriterion criterion = onlyCriterion(filter("safetyRating", "EQUALS", "HIGH"));

            assertThat(criterion.field()).isEqualTo(VehicleField.SAFETY_RATING);
            assertThat(criterion.comparison()).isEqualTo(Comparison.OVER);
            assertThat(criterion.single()).isEqualTo(4);
        }

        @Test
        void lowKilometresBecomesAnUpperBound() {
            FieldCriterion criterion = onlyCriterion(filter("kilometres", "EQUALS", "LOW"));

            assertThat(criterion.comparison()).isEqualTo(Comparison.UNDER);
            assertThat(criterion.single()).isEqualTo(30_000);
        }

        @Test
        void highKilometresBecomesALowerBound() {
            FieldCriterion criterion = onlyCriterion(filter("kilometres", "EQUALS", "HIGH"));

            assertThat(criterion.comparison()).isEqualTo(Comparison.OVER);
            assertThat(criterion.single()).isEqualTo(100_000);
        }

        @Test
        void dropsALevelTheFieldDoesNotDefine() {
            // Kilometres has LOW and HIGH but no MEDIUM. Snapping to the nearest band would
            // answer a question the shopper did not ask.
            assertThat(onlyDroppedFilter(filter("kilometres", "EQUALS", "MEDIUM")).reason())
                    .isEqualTo("'kilometres' has no MEDIUM band; defined levels are LOW, HIGH");
        }

        @Test
        void dropsAMagnitudeOnAFieldWithNoBands() {
            // "cheap" is not a threshold this service can defend: it depends entirely on the
            // segment. The model has to resolve budget words into rupees or say nothing.
            assertThat(onlyDroppedFilter(filter("price", "EQUALS", "LOW")).reason())
                    .contains("no magnitude bands");
        }

        @Test
        void dropsAMagnitudeCarryingItsOwnComparison() {
            // The band supplies the comparison. "safetyRating UNDER HIGH" has two answers to the
            // same question, so it is a contradiction rather than something to reconcile.
            assertThat(onlyDroppedFilter(filter("safetyRating", "UNDER", "HIGH")).reason())
                    .contains("must be paired with EQUALS");
        }

        private FieldCriterion onlyCriterion(ParsedFilter filter) {
            SearchCriteria criteria = validator.validate(
                    new ParsedQuery(List.of(filter), List.of())).criteria();
            assertThat(criteria.filters()).hasSize(1);
            return criteria.filters().get(0);
        }
    }

    @Nested
    @DisplayName("drops what it cannot honour and runs the rest")
    class Drops {

        @Test
        void unknownField() {
            assertThat(onlyDroppedFilter(filter("mileage", "OVER", "20")))
                    .extracting(DroppedFilter::field, DroppedFilter::reason)
                    .containsExactly("mileage", "unknown field 'mileage'");
        }

        @Test
        void comparisonOutsideTheAllowedFour() {
            assertThat(onlyDroppedFilter(filter("colour", "NOT_EQUALS", "White")).reason())
                    .isEqualTo("unknown comparison 'NOT_EQUALS'; allowed are UNDER, OVER, EQUALS, BETWEEN");
        }

        @Test
        void orderedComparisonOnAnUnorderedField() {
            assertThat(onlyDroppedFilter(filter("colour", "UNDER", "White")).reason())
                    .contains("is not");
        }

        @Test
        void wrongNumberOfValues() {
            assertThat(onlyDroppedFilter(filter("price", "BETWEEN", "500000")).reason())
                    .isEqualTo("BETWEEN takes 2 value(s), got 1");
        }

        @Test
        void rangeRunningBackwards() {
            assertThat(onlyDroppedFilter(filter("price", "BETWEEN", "1000000", "500000")).reason())
                    .contains("above upper bound");
        }

        @Test
        void valueOfTheWrongType() {
            assertThat(onlyDroppedFilter(filter("price", "UNDER", "15 lakh")).reason())
                    .contains("not a plain rupee amount");
        }

        @Test
        void valueOutsideAnEnum() {
            assertThat(onlyDroppedFilter(filter("fuelType", "EQUALS", "HYDROGEN")).reason())
                    .contains("is not a valid fuelType");
        }

        @Test
        void bodyTypeOutsideTheCatalogue() {
            // "convertibles under 20 lakh". The price still runs; the convertible is reported, not
            // applied.
            Interpretation interpretation = validator.validate(new ParsedQuery(
                    List.of(
                            filter("price", "UNDER", "2000000"),
                            filter("bodyType", "EQUALS", "CONVERTIBLE")),
                    List.of()));

            assertThat(interpretation.criteria().filters()).extracting(FieldCriterion::field)
                    .containsExactly(VehicleField.PRICE);
            assertThat(interpretation.ignoredFilters()).hasSize(1);
            assertThat(interpretation.ignoredFilters().get(0).reason())
                    .contains("is not a valid bodyType");
        }

        @Test
        void unknownFeatureTag() {
            Interpretation interpretation = validator.validate(
                    new ParsedQuery(List.of(VALID), List.of("teleport_mode")));

            assertThat(interpretation.criteria().features()).isEmpty();
            assertThat(interpretation.ignoredFeatures())
                    .extracting(DroppedFeature::tag, DroppedFeature::reason)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("teleport_mode", "unknown feature tag"));
        }

        @Test
        void keepsTheGoodPartsOfAPartlyBadQuery() {
            // The headline behaviour: three of four conditions are usable, so three of four run.
            Interpretation interpretation = validator.validate(new ParsedQuery(
                    List.of(
                            filter("bodyType", "EQUALS", "SUV"),
                            filter("price", "UNDER", "1500000"),
                            filter("mileage", "OVER", "20")),
                    List.of("sunroof", "teleport_mode")));

            assertThat(interpretation.criteria().filters()).hasSize(2);
            assertThat(interpretation.criteria().features()).containsExactly("sunroof");
            assertThat(interpretation.ignoredFilters()).extracting(DroppedFilter::field)
                    .containsExactly("mileage");
            assertThat(interpretation.ignoredFeatures()).extracting(DroppedFeature::tag)
                    .containsExactly("teleport_mode");
            assertThat(interpretation.droppedCount()).isEqualTo(2);
        }

        @Test
        void classifiesAnUnknownFieldAsTheShopperAskingForSomethingWeLack() {
            // This is the channel the prompt uses for anything the catalogue cannot express,
            // including negation. It must never page anyone.
            assertThat(onlyDroppedFilter(filter("fuelEfficiencyKmpl", "OVER", "20")).category())
                    .isEqualTo(DropCategory.UNSUPPORTED_REQUEST);
            assertThat(onlyDroppedFilter(filter("colourExclusion", "EQUALS", "White")).category())
                    .isEqualTo(DropCategory.UNSUPPORTED_REQUEST);
        }

        @Test
        void classifiesAContractBreachOnAKnownFieldAsDrift() {
            // Nothing a shopper types produces these, so the baseline is zero and each one is
            // worth waking someone for.
            assertThat(onlyDroppedFilter(filter("price", "UNDER", "15 lakh")).category())
                    .isEqualTo(DropCategory.PARSER_DRIFT);
            assertThat(onlyDroppedFilter(filter("price", "BETWEEN", "500000")).category())
                    .isEqualTo(DropCategory.PARSER_DRIFT);
            assertThat(onlyDroppedFilter(filter("colour", "UNDER", "White")).category())
                    .isEqualTo(DropCategory.PARSER_DRIFT);
            assertThat(onlyDroppedFilter(filter("kilometres", "EQUALS", "MEDIUM")).category())
                    .isEqualTo(DropCategory.PARSER_DRIFT);
        }

        @Test
        void classifiesAnUnknownTagAsAnUnsupportedRequestButABlankTagAsDrift() {
            assertThat(validator.validate(new ParsedQuery(List.of(VALID), List.of("massage_seats")))
                    .ignoredFeatures().get(0).category())
                    .isEqualTo(DropCategory.UNSUPPORTED_REQUEST);

            assertThat(validator.validate(new ParsedQuery(List.of(VALID), List.of("  ")))
                    .ignoredFeatures().get(0).category())
                    .isEqualTo(DropCategory.PARSER_DRIFT);
        }

        @Test
        void echoesADroppedFilterBackExactlyAsItArrived() {
            // The shopper has to be able to recognise the condition they asked for.
            DroppedFilter dropped = onlyDroppedFilter(filter("mileage", "OVER", "20"));

            assertThat(dropped.field()).isEqualTo("mileage");
            assertThat(dropped.comparison()).isEqualTo("OVER");
            assertThat(dropped.values()).containsExactly("20");
        }
    }

    @Nested
    @DisplayName("still refuses output it cannot use at all")
    class Refuses {

        @Test
        void aQueryWhereEverythingWasDropped() {
            // Running what remains is right up until nothing remains: an empty WHERE clause
            // returns the whole catalogue and looks like a working search.
            NoSearchableConditionsException thrown = catchThrowableOfType(
                    () -> validator.validate(new ParsedQuery(
                            List.of(filter("mileage", "OVER", "20")), List.of("teleport_mode"))),
                    NoSearchableConditionsException.class);

            assertThat(thrown).hasMessageContaining("None of the 2 condition(s)");
            assertThat(thrown.ignoredFilters()).hasSize(1);
            assertThat(thrown.ignoredFeatures()).hasSize(1);
            // The categories have to survive the throw: this is the path where the parser broke
            // every condition at once, which is precisely when the drift alarm must still fire.
            assertThat(thrown.ignoredFilters().get(0).category())
                    .isEqualTo(DropCategory.UNSUPPORTED_REQUEST);
            assertThat(thrown.ignoredFeatures().get(0).category())
                    .isEqualTo(DropCategory.UNSUPPORTED_REQUEST);
        }

        @Test
        void anInterpretationWithNothingInIt() {
            assertThatThrownBy(() -> validator.validate(ParsedQuery.empty()))
                    .isInstanceOf(NoSearchableConditionsException.class)
                    .hasMessageContaining("No filters or features");
        }

        @Test
        void moreFiltersThanAnyRealQueryHas() {
            // Not a query with some bad parts to drop -- a parser that has stopped working. There
            // is no sensible subset of 21 conditions to honour, so this one stays fatal.
            List<ParsedFilter> tooMany = new ArrayList<>(
                    IntStream.range(0, 21).mapToObj(i -> VALID).toList());

            assertThatThrownBy(() -> validator.validate(new ParsedQuery(tooMany, List.of())))
                    .isInstanceOf(InvalidParsedQueryException.class)
                    .hasMessageContaining("too many filters: 21, limit is 20");
        }

        @Test
        void moreFeaturesThanAnyRealQueryHas() {
            List<String> tooMany = IntStream.range(0, 21).mapToObj(i -> "sunroof").toList();

            assertThatThrownBy(() -> validator.validate(new ParsedQuery(List.of(VALID), tooMany)))
                    .isInstanceOf(InvalidParsedQueryException.class)
                    .hasMessageContaining("too many features: 21, limit is 20");
        }
    }

    /** Validates the given filter alongside one that always survives, and returns the drop. */
    private DroppedFilter onlyDroppedFilter(ParsedFilter bad) {
        Interpretation interpretation = validator.validate(
                new ParsedQuery(List.of(VALID, bad), List.of()));

        assertThat(interpretation.criteria().filters()).hasSize(1);
        assertThat(interpretation.ignoredFilters()).hasSize(1);
        return interpretation.ignoredFilters().get(0);
    }

    private static ParsedFilter filter(String field, String comparison, String... values) {
        return new ParsedFilter(field, comparison, List.of(values));
    }
}
