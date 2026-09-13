package com.cars24.carsearch.vocabulary;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders the searchable vocabulary as prompt text.
 *
 * <p>Every line is generated from the enums in this package -- the comparisons from
 * {@link Comparison}, the fields and their types from {@link VehicleField}, the enum values from
 * each field's own Java enum, the magnitude levels from {@link FuzzyBands}, the tags from
 * {@link FeatureCatalogue}. None of it is typed out by hand anywhere.
 *
 * <p>That is the point. A prompt with a hand-written field list is a second copy of the contract
 * that drifts from the validator the first time someone adds a column, and the symptom is a model
 * confidently emitting a field that gets dropped on arrival. Generating both from one source makes
 * that impossible: what the model is told it may say and what the validator accepts are the same
 * list, read from the same place.
 *
 * <p>Rendered once at startup. The text is identical on every request, which is what lets it sit
 * in front of the cache breakpoint.
 */
@Component
public class SearchContract {

    private final String rendered;

    public SearchContract(FeatureCatalogue featureCatalogue) {
        this.rendered = render(featureCatalogue);
    }

    public String asPromptSection() {
        return rendered;
    }

    private static String render(FeatureCatalogue featureCatalogue) {
        StringBuilder out = new StringBuilder();

        out.append("## Comparisons\n\n");
        out.append("These four are the entire set. There is no not-equals and no one-of.\n\n");
        for (Comparison comparison : Comparison.values()) {
            out.append(String.format("- %-8s %-24s takes %d value%s%n",
                    comparison.name(),
                    meaningOf(comparison),
                    comparison.arity(),
                    comparison.arity() == 1 ? "" : "s"));
        }

        out.append("\n## Fields\n\n");
        out.append("Use these names exactly. Anything not on this list is not a field.\n\n");
        for (VehicleField field : VehicleField.values()) {
            out.append("- ").append(field.wireName()).append(" — ").append(typeOf(field));
            out.append("; accepts ").append(comparisonsFor(field));
            if (FuzzyBands.hasBands(field)) {
                out.append("; magnitudes ").append(FuzzyBands.levelsFor(field));
            }
            out.append('\n');
        }

        out.append("\n## Feature tags\n\n");
        out.append("Use these exactly as written. Do not invent, pluralise, or abbreviate them.\n\n");
        out.append(featureCatalogue.all().stream().sorted().collect(Collectors.joining(", ")));
        out.append('\n');

        Map<String, String> notes = featureCatalogue.notes();
        if (!notes.isEmpty()) {
            out.append("\n### Tag notes\n\n");
            notes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> out.append("- ").append(e.getKey()).append(" — ")
                            .append(e.getValue()).append('\n'));
        }

        return out.toString();
    }

    private static String meaningOf(Comparison comparison) {
        return switch (comparison) {
            case UNDER -> "field <= value";
            case OVER -> "field >= value";
            case EQUALS -> "field = value";
            case BETWEEN -> "low <= field <= high";
        };
    }

    private static String typeOf(VehicleField field) {
        return switch (field.valueType()) {
            case TEXT -> "text";
            case MONEY -> "whole rupees, digits only";
            case INTEGER -> "whole number, digits only";
            case ENUM -> "one of " + Arrays.stream(field.enumType().getEnumConstants())
                    .map(Enum::name).collect(Collectors.joining(", "));
        };
    }

    private static String comparisonsFor(VehicleField field) {
        return Arrays.stream(Comparison.values())
                .filter(field::supports)
                .map(Comparison::name)
                .collect(Collectors.joining(", "));
    }
}
