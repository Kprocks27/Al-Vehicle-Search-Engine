package com.cars24.carsearch.validation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The parser returned something we refuse to run.
 *
 * <p>This is an upstream failure, not a user error. The shopper's sentence may have been
 * perfectly reasonable; the component that interpreted it produced structure that names a column
 * we do not have, or a comparison outside the four, or a feature nobody stocks. Which is why it
 * surfaces as a 502 rather than a 400 -- and why it carries every violation rather than the first
 * one. When these show up in logs they are prompt-tuning input, and the second and third
 * violation are often the informative ones.
 */
public class InvalidParsedQueryException extends RuntimeException {

    private final transient List<Violation> violations;

    public InvalidParsedQueryException(List<Violation> violations) {
        super("Parser output rejected: "
                + violations.stream().map(Violation::toString).collect(Collectors.joining("; ")));
        this.violations = List.copyOf(violations);
    }

    public List<Violation> violations() {
        return violations;
    }
}
