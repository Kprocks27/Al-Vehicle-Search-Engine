package com.cars24.carsearch.validation;

/**
 * One thing wrong with the parser's output.
 *
 * @param location where it was, e.g. "filters[2].comparison" -- so a log line points at the
 *                 offending element rather than just saying the query was bad
 * @param message  what was wrong, phrased for whoever debugs the prompt
 */
public record Violation(String location, String message) {

    @Override
    public String toString() {
        return location + ": " + message;
    }
}
