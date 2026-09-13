package com.cars24.carsearch.nlq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/**
 * Caches interpretations, not results.
 *
 * <p>Inventory turns over constantly -- cars sell, prices drop, new stock lands -- so caching the
 * cars that came back would serve sold vehicles. But the meaning of "SUVs under 15 lakh" does not
 * change: it is bodyType = SUV and price &lt;= 1500000 today and next month. So the cache sits
 * here, between the sentence and the conditions, and the database is queried fresh every time.
 *
 * <p>A decorator rather than an annotation on the parser itself, for two reasons. The stub and
 * the eventual LLM implementation get identical caching without either knowing about it, and the
 * caching behaviour is testable with a counting fake and no Spring context.
 */
public class CachingQueryParser implements QueryParser {

    private static final Logger log = LoggerFactory.getLogger(CachingQueryParser.class);

    private final QueryParser delegate;
    private final Cache cache;

    public CachingQueryParser(QueryParser delegate, Cache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public ParsedQuery parse(String query) {
        String key = QueryNormalizer.normalize(query);
        ParsedQuery cached = cache.get(key, ParsedQuery.class);
        if (cached != null) {
            return cached;
        }

        log.debug("Interpretation cache miss for '{}', calling parser", key);
        ParsedQuery parsed = delegate.parse(query);
        cache.put(key, parsed);
        return parsed;
    }
}
