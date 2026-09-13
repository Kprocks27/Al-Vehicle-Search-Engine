package com.cars24.carsearch.vocabulary;

import java.util.Locale;
import java.util.Optional;

/**
 * The vague magnitudes a parser is allowed to hand back instead of a number.
 *
 * <p>The model is good at spotting that "high safety" is a magnitude rather than a number. It is
 * bad at being consistent about which number that is -- ask three times and you may get 4, 4.5
 * and "above average". So the model's job stops at naming the level; {@link FuzzyBands} owns the
 * threshold. The thresholds then live in one file, in version control, and are the same on every
 * call.
 */
public enum FuzzyLevel {

    LOW,
    MEDIUM,
    HIGH;

    public static Optional<FuzzyLevel> fromToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        for (FuzzyLevel level : values()) {
            if (level.name().equals(token.trim().toUpperCase(Locale.ROOT))) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }
}
