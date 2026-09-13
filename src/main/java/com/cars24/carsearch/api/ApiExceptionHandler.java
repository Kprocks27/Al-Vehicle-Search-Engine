package com.cars24.carsearch.api;

import com.cars24.carsearch.api.dto.IgnoredFeatureView;
import com.cars24.carsearch.api.dto.IgnoredFilterView;
import com.cars24.carsearch.nlq.ParserUnavailableException;
import com.cars24.carsearch.nlq.QueryNotUnderstoodException;
import com.cars24.carsearch.validation.InvalidParsedQueryException;
import com.cars24.carsearch.validation.NoSearchableConditionsException;
import com.cars24.carsearch.validation.Violation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Failure responses, and the reasoning behind which status each failure gets.
 *
 * <p>The distinction that matters here is whose fault it was. A sentence nobody could interpret
 * is the shopper's to rephrase, so it is a 4xx and the message tells them so. Parser output so
 * malformed that we refuse to run it at all is ours: the shopper may have asked something
 * perfectly sensible and the component we depend on returned nonsense. That is a 502, logged at
 * error, and it is the alert that says a prompt or a model version has drifted.
 *
 * <p>Since individual unusable conditions are now dropped rather than fatal, the 502 is rare: it
 * means the parser returned more conditions than any real query has. Most drift shows up instead
 * as the WARN that {@code VehicleSearchService} logs on a dropped condition.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Everything was dropped, so there is nothing to search. Still a 422 -- not enough of the
     * sentence landed -- but it carries the dropped lists, because a query that fails outright
     * has to explain itself just as clearly as one that partially succeeds.
     */
    @ExceptionHandler(NoSearchableConditionsException.class)
    public ProblemDetail handleNothingSearchable(NoSearchableConditionsException e) {
        log.info("Nothing searchable left: {}", e.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Query not understood");
        problem.setProperty("ignoredFilters",
                e.ignoredFilters().stream().map(IgnoredFilterView::from).toList());
        problem.setProperty("ignoredFeatures",
                e.ignoredFeatures().stream().map(IgnoredFeatureView::from).toList());
        return problem;
    }

    @ExceptionHandler(QueryNotUnderstoodException.class)
    public ProblemDetail handleNotUnderstood(QueryNotUnderstoodException e) {
        log.info("Query not understood: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Query not understood");
        return problem;
    }

    @ExceptionHandler(InvalidParsedQueryException.class)
    public ProblemDetail handleInvalidParse(InvalidParsedQueryException e) {
        // Error, not warn: every one of these is a defect in the interpretation layer, and the
        // violation list is what the fix gets written from.
        log.error("Rejected parser output: {}", e.violations());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "The query could not be turned into a valid search. This is a fault on our side.");
        problem.setTitle("Interpretation rejected");
        // The specifics go to the client too: this service is internal, and a front end that can
        // show the failing condition saves a round trip through logs during development.
        problem.setProperty("violations", e.violations().stream().map(Violation::toString).toList());
        return problem;
    }

    /**
     * The interpretation layer did not answer -- down, timed out, refused, or off-schema. Nothing
     * about the shopper's sentence needs to change, so this is a 503 and not a 4xx: the same query
     * may well work on the next attempt.
     */
    @ExceptionHandler(ParserUnavailableException.class)
    public ProblemDetail handleParserUnavailable(ParserUnavailableException e) {
        log.error("Query interpreter unavailable", e);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Search is temporarily unavailable. Please try again.");
        problem.setTitle("Interpreter unavailable");
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        List<String> messages = e.getConstraintViolations().stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .sorted()
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, String.join("; ", messages));
        problem.setTitle("Invalid request");
        return problem;
    }
}
