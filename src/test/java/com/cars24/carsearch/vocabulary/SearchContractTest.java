package com.cars24.carsearch.vocabulary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The prompt and the validator have to describe the same contract. These tests are the guard: if
 * someone adds a column, a comparison or a tag and the prompt stops mentioning it, this fails
 * rather than the model quietly emitting something that gets dropped on arrival.
 */
@SpringBootTest
class SearchContractTest {

    @Autowired
    private SearchContract searchContract;

    @Autowired
    private FeatureCatalogue featureCatalogue;

    @Test
    void namesEverySearchableField() {
        String rendered = searchContract.asPromptSection();
        for (VehicleField field : VehicleField.values()) {
            assertThat(rendered).contains(field.wireName());
        }
    }

    @Test
    void namesEveryComparisonAndNoOthers() {
        String rendered = searchContract.asPromptSection();
        for (Comparison comparison : Comparison.values()) {
            assertThat(rendered).contains(comparison.name());
        }
        assertThat(rendered).doesNotContain("NOT_EQUALS", "ONE_OF", "IN ");
    }

    @Test
    void namesEveryFeatureTag() {
        String rendered = searchContract.asPromptSection();
        assertThat(featureCatalogue.all()).isNotEmpty();
        for (String tag : featureCatalogue.all()) {
            assertThat(rendered).contains(tag);
        }
    }

    @Test
    void advertisesTheMagnitudesEachFieldActuallyDefines() {
        String rendered = searchContract.asPromptSection();

        assertThat(rendered).contains("kilometres").contains("magnitudes LOW, HIGH");
        assertThat(rendered).contains("magnitudes LOW, MEDIUM, HIGH");
        // price has no bands, so the prompt must not offer any for it
        String priceLine = Arrays.stream(rendered.split("\n"))
                .filter(l -> l.startsWith("- price "))
                .findFirst().orElseThrow();
        assertThat(priceLine).doesNotContain("magnitudes");
    }

    @Test
    void reportsAssembledPromptSize() throws Exception {
        String vocabulary = searchContract.asPromptSection();
        String rules = new String(new ClassPathResource("prompt/query-parser.md")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String full = "# The search vocabulary\n\n" + vocabulary + "\n# How to read a query\n\n" + rules;

        int chars = full.length();
        int words = full.split("\\s+").length;
        System.out.printf("%n=== assembled system prompt ===%n");
        System.out.printf("  vocabulary section : %,d chars%n", vocabulary.length());
        System.out.printf("  rules section      : %,d chars%n", rules.length());
        System.out.printf("  TOTAL              : %,d chars, %,d whitespace-separated words%n", chars, words);
        System.out.printf("  rough token range  : %,d - %,d  (chars/4 .. chars/3)%n", chars / 4, chars / 3);
        System.out.printf("  caching minimums   : Opus 5 = 512, Sonnet 5 = 1024, Haiku 4.5 = 4096%n%n");

        assertThat(chars).isPositive();
    }
}
