package com.cars24.carsearch.nlq;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cache exists to stop us paying for the same LLM call twice, so the thing worth testing is
 * how many times the delegate is actually invoked -- not what comes back.
 */
class CachingQueryParserTest {

    private final AtomicInteger delegateCalls = new AtomicInteger();

    @Test
    void callsTheParserOncePerDistinctQuery() {
        CachingQueryParser parser = new CachingQueryParser(countingParser(), cache());

        parser.parse("SUVs under 15 lakh");
        parser.parse("SUVs under 15 lakh");

        assertThat(delegateCalls).hasValue(1);
    }

    @Test
    void treatsCasingAndSpacingAsTheSameQuery() {
        // These three are the same request typed by three people. Paying an LLM three times for
        // them is the easiest money this service can save.
        CachingQueryParser parser = new CachingQueryParser(countingParser(), cache());

        parser.parse("SUVs under 15 lakh");
        parser.parse("suvs under 15 lakh");
        parser.parse("  SUVs   under    15 lakh  ");

        assertThat(delegateCalls).hasValue(1);
    }

    @Test
    void treatsDifferentQueriesSeparately() {
        CachingQueryParser parser = new CachingQueryParser(countingParser(), cache());

        parser.parse("SUVs under 15 lakh");
        parser.parse("SUVs under 10 lakh");

        assertThat(delegateCalls).hasValue(2);
    }

    @Test
    void doesNotCacheFailures() {
        // A parse that blew up may have blown up for a transient reason. Caching the failure
        // would make one bad call stick to a query for as long as the entry lives.
        QueryParser failing = query -> {
            delegateCalls.incrementAndGet();
            throw new QueryNotUnderstoodException("nope");
        };
        CachingQueryParser parser = new CachingQueryParser(failing, cache());

        assertThatThrownBy(() -> parser.parse("something odd"))
                .isInstanceOf(QueryNotUnderstoodException.class);
        assertThatThrownBy(() -> parser.parse("something odd"))
                .isInstanceOf(QueryNotUnderstoodException.class);

        assertThat(delegateCalls).hasValue(2);
    }

    private QueryParser countingParser() {
        return query -> {
            delegateCalls.incrementAndGet();
            return new ParsedQuery(List.of(new ParsedFilter("bodyType", "EQUALS", List.of("SUV"))), List.of());
        };
    }

    private static ConcurrentMapCache cache() {
        return new ConcurrentMapCache("queryInterpretations");
    }
}
