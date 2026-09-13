package com.cars24.carsearch.nlq.claude;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.cars24.carsearch.vocabulary.Comparison;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON schema the model's output is constrained to.
 *
 * <p>Generated, like the prompt text, rather than written out -- the list of comparisons comes from
 * {@link Comparison#values()}, so the schema cannot offer the model a comparison the validator
 * would reject, or omit one it would accept.
 *
 * <p>What is deliberately <em>not</em> constrained:
 *
 * <ul>
 *   <li>{@code field} is a free string. It has to be: the prompt instructs the model to surface
 *       anything the catalogue cannot express as a descriptively-named field that does not exist,
 *       which is precisely what an enum would forbid.
 *   <li>{@code features} are free strings, so an unknown tag arrives, gets dropped, and gets
 *       reported to the shopper. Pinning the tag list here would make hallucinated tags impossible
 *       but would also make "do you have massage seats?" silently unanswerable.
 *   <li>No upper bound on any array. Structured outputs rejects {@code maxItems}, so the ceilings
 *       live only in {@code ParsedQueryValidator} -- which is where they were always enforced
 *       anyway. Nothing is lost: the schema could never express "one value unless BETWEEN, then
 *       two" without a conditional, so per-comparison arity was always the validator's job, and a
 *       wrong value count still surfaces as parser drift.
 * </ul>
 */
final class QuerySchema {

    private QuerySchema() {
    }

    static JsonOutputFormat build() {
        Map<String, Object> filter = object(
                Map.of(
                        "field", Map.of(
                                "type", "string",
                                "description", "A field name from the Fields list, or -- for a "
                                        + "constraint the catalogue cannot express -- a descriptive "
                                        + "name that is deliberately not one of them."),
                        "comparison", Map.of(
                                "type", "string",
                                "enum", Arrays.stream(Comparison.values()).map(Enum::name).toList()),
                        "values", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "minItems", 1,
                                "description", "Plain digit strings for numbers -- no symbols, "
                                        + "commas, or unit words. Two values for BETWEEN, one "
                                        + "otherwise.")),
                List.of("field", "comparison", "values"));

        Map<String, Object> root = object(
                Map.of(
                        "filters", Map.of("type", "array", "items", filter),
                        "features", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Bare feature tags. Never a field name.")),
                List.of("filters", "features"));

        return JsonOutputFormat.builder()
                .schema(JsonOutputFormat.Schema.builder()
                        .additionalProperties(toJsonValues(root))
                        .build())
                .build();
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "object");
        node.put("properties", properties);
        node.put("required", required);
        node.put("additionalProperties", false);
        return node;
    }

    private static Map<String, JsonValue> toJsonValues(Map<String, Object> map) {
        Map<String, JsonValue> out = new LinkedHashMap<>();
        map.forEach((key, value) -> out.put(key, JsonValue.from(value)));
        return out;
    }
}
