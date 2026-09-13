package com.cars24.carsearch.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The interpretation cache.
 *
 * <p>Caffeine behind Spring's {@code CacheManager} rather than a raw map. The abstraction is what
 * makes the eventual move to Redis a configuration change: once this runs on more than one
 * instance, a shared cache means the second instance does not pay for an LLM call the first one
 * already made.
 *
 * <p>Both bounds matter. {@code maximumSize} because query text is attacker-controlled and an
 * unbounded map keyed on it is a memory leak waiting for a bored user. {@code expireAfterWrite}
 * because an interpretation is only as good as the prompt and the field vocabulary that produced
 * it -- a process restart clears an in-memory cache today, but a shared cache would happily serve
 * yesterday's interpretation after a deploy, and a day is a reasonable ceiling on that.
 */
@Configuration
public class CacheConfig {

    public static final String INTERPRETATION_CACHE = "queryInterpretations";

    @Bean
    public CacheManager cacheManager(@Value("${carsearch.cache.spec}") String spec) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(INTERPRETATION_CACHE);
        cacheManager.setCaffeine(Caffeine.from(spec));
        return cacheManager;
    }
}
