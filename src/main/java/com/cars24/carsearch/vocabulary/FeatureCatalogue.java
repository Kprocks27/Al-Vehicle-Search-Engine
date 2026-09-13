package com.cars24.carsearch.vocabulary;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The known feature tags.
 *
 * <p>Features are open-ended in the schema -- a tag set, so a new feature is a data change, not a
 * migration. But "open-ended" cannot mean "whatever the model says", or a hallucinated tag turns
 * into a silent zero-result query that looks like a stock problem rather than a bug. So the
 * vocabulary is an allow-list, and it lives in configuration rather than in a Java enum: adding a
 * feature is a config change plus a prompt change, with no recompile and no schema touch.
 *
 * <p>This same set is what gets injected into the model's prompt when the real parser lands, so
 * the list the model is told about and the list we validate against cannot drift apart.
 */
@Component
@ConfigurationProperties(prefix = "carsearch.vocabulary")
public class FeatureCatalogue {

    private Set<String> features = Set.of();
    private Map<String, String> featureNotes = Map.of();

    /** Bound from configuration; normalised once here so lookups are plain set membership. */
    public void setFeatures(List<String> features) {
        Set<String> normalised = new LinkedHashSet<>();
        for (String feature : features) {
            if (feature != null && !feature.isBlank()) {
                normalised.add(normalise(feature));
            }
        }
        this.features = Set.copyOf(normalised);
    }

    /**
     * Short glosses for the handful of tags a model cannot reliably guess from the name alone --
     * acronyms, and pairs where one tag is a narrower case of another. Most tags need none;
     * glossing all 38 would bury the ones that matter and push the model toward matching on prose
     * rather than on the tag itself.
     */
    public void setFeatureNotes(Map<String, String> featureNotes) {
        Map<String, String> normalised = new LinkedHashMap<>();
        featureNotes.forEach((tag, note) -> normalised.put(normalise(tag), note));
        this.featureNotes = Map.copyOf(normalised);
    }

    /**
     * A note for a tag that does not exist is a silent no-op that nobody notices until a model
     * starts guessing. Fail at startup instead.
     */
    @PostConstruct
    void verifyNotesMatchVocabulary() {
        Set<String> unknown = new LinkedHashSet<>(featureNotes.keySet());
        unknown.removeAll(features);
        if (!unknown.isEmpty()) {
            throw new IllegalStateException(
                    "carsearch.vocabulary.feature-notes refers to tags that are not in "
                            + "carsearch.vocabulary.features: " + unknown);
        }
    }

    public Map<String, String> notes() {
        return featureNotes;
    }

    public boolean contains(String tag) {
        return tag != null && features.contains(normalise(tag));
    }

    /** The canonical form of a tag, for storing on the criteria and matching the tag column. */
    public String canonicalise(String tag) {
        return normalise(tag);
    }

    public Set<String> all() {
        return features;
    }

    private static String normalise(String tag) {
        return tag.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
