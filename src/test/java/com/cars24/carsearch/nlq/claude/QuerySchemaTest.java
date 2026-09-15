package com.cars24.carsearch.nlq.claude;

import com.anthropic.core.ObjectMappers;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structured output writes an object's keys in the order the schema lists them, so that order is
 * the order the model decides things in -- at both levels. Inside a filter, {@code field} and
 * {@code comparison} have to come before {@code values}: with {@code values} first, "from 5 to 10
 * lakhs" came back as {@code price BETWEEN [""]}. At the top level, the order of {@code filters}
 * and {@code features} changed how often that happened, so it is pinned too; the combination
 * tested here is the one that measured clean. When the schema was built from {@code Map.of}, both
 * orders were reshuffled on every JVM start.
 */
class QuerySchemaTest {

    @Test
    void filterKeysRunFieldThenComparisonThenValues() {
        JsonNode filter = schema().at("/schema/properties/filters/items/properties");

        assertThat(keysOf(filter)).containsExactly("field", "comparison", "values");
    }

    @Test
    void rootKeysRunFiltersThenFeatures() {
        JsonNode root = schema().at("/schema/properties");

        assertThat(keysOf(root)).containsExactly("filters", "features");
    }

    private static JsonNode schema() {
        // Serialised by the SDK's own mapper, so this is the order that actually goes over the wire.
        return ObjectMappers.jsonMapper().valueToTree(QuerySchema.build());
    }

    private static List<String> keysOf(JsonNode node) {
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}
