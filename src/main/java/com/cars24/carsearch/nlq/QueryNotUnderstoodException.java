package com.cars24.carsearch.nlq;

/**
 * The parser could not interpret the text at all.
 *
 * <p>Distinct from a validation failure. This means the shopper's sentence did not land -- their
 * problem to fix, so it surfaces as a 4xx. A validation failure means the parser handed back
 * something malformed, which is our problem, and surfaces as a 5xx.
 */
public class QueryNotUnderstoodException extends RuntimeException {

    public QueryNotUnderstoodException(String message) {
        super(message);
    }
}
