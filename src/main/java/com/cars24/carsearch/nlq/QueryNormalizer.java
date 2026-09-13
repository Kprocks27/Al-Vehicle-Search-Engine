package com.cars24.carsearch.nlq;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonical form of a query string, used as the cache key.
 *
 * <p>Lowercase, trim, collapse internal whitespace. That is the whole rule, and it is
 * intentionally conservative: every transform here asserts that two different strings mean the
 * same thing, and an over-eager normaliser (stripping punctuation, dropping stop words, stemming)
 * eventually collapses two queries that a shopper meant differently and serves the wrong cached
 * interpretation. Whitespace and case are the two transforms that are always safe.
 */
public final class QueryNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private QueryNormalizer() {
    }

    public static String normalize(String query) {
        if (query == null) {
            return "";
        }
        return WHITESPACE.matcher(query.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
