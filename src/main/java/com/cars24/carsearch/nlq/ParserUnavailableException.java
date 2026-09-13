package com.cars24.carsearch.nlq;

/**
 * The parser could not be reached, or returned something unusable as a whole.
 *
 * <p>Distinct from both of the other failures. {@code QueryNotUnderstoodException} means the
 * sentence did not land -- rephrasing helps. {@code InvalidParsedQueryException} means the parser
 * answered, badly. This means it did not usefully answer at all: the API is down, the request timed
 * out, the model refused, or the response was not the shape the schema promised.
 *
 * <p>Retrying the same sentence later may well work, which is why it surfaces as a 503 rather than
 * a 4xx -- nothing about the shopper's query needs to change.
 */
public class ParserUnavailableException extends RuntimeException {

    public ParserUnavailableException(String message) {
        super(message);
    }

    public ParserUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
