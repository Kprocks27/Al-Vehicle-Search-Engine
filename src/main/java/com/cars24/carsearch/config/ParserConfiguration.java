package com.cars24.carsearch.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.cars24.carsearch.nlq.CachingQueryParser;
import com.cars24.carsearch.nlq.QueryParser;
import com.cars24.carsearch.nlq.claude.ClaudeParserProperties;
import com.cars24.carsearch.nlq.claude.ClaudeQueryParser;
import com.cars24.carsearch.nlq.stub.StubQueryParser;
import com.cars24.carsearch.vocabulary.SearchContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * How the parser is assembled. One file, so the whole wiring is readable at a glance.
 *
 * <p>Two interpreters exist and exactly one is ever active, chosen by
 * {@code carsearch.parser.implementation}. The stub is the default, which is what keeps the test
 * suite runnable in CI with no API key and no spend -- a build that needs a credential to go green
 * is a build that breaks for every new contributor.
 *
 * <p>Whichever is active gets wrapped in the same cache. Neither knows about it.
 */
@Configuration
@EnableConfigurationProperties(ClaudeParserProperties.class)
public class ParserConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ParserConfiguration.class);

    @Bean(name = "queryInterpreter")
    @ConditionalOnProperty(prefix = "carsearch.parser", name = "implementation",
            havingValue = "stub", matchIfMissing = true)
    public QueryParser stubInterpreter() {
        log.info("Query interpreter: stub (fixture-backed, no API calls)");
        return new StubQueryParser();
    }

    @Bean(name = "queryInterpreter")
    @ConditionalOnProperty(prefix = "carsearch.parser", name = "implementation",
            havingValue = "claude")
    public QueryParser claudeInterpreter(ClaudeParserProperties properties,
                                         SearchContract searchContract,
                                         ObjectMapper objectMapper) {
        // Fail at startup with a sentence that says what to do, rather than on the first search
        // with whatever the SDK throws when it finds no credential.
        if (System.getenv("ANTHROPIC_API_KEY") == null || System.getenv("ANTHROPIC_API_KEY").isBlank()) {
            throw new IllegalStateException(
                    "carsearch.parser.implementation=claude needs ANTHROPIC_API_KEY in the "
                            + "environment. Export it, or run with the default stub parser.");
        }

        AnthropicClient client = AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(properties.timeout())
                .build();

        String systemPrompt = buildSystemPrompt(searchContract);
        log.info("Query interpreter: Claude ({}, effort={}), system prompt {} chars",
                properties.model(),
                properties.hasEffort() ? properties.effort() : "unset",
                systemPrompt.length());

        return new ClaudeQueryParser(client, properties, systemPrompt, objectMapper);
    }

    @Bean
    @Primary
    public QueryParser cachingQueryParser(@Qualifier("queryInterpreter") QueryParser delegate,
                                          CacheManager cacheManager) {
        return new CachingQueryParser(delegate, cacheManager.getCache(CacheConfig.INTERPRETATION_CACHE));
    }

    /**
     * Vocabulary first, then the rules that use it. Both halves are fixed at startup, so the whole
     * string is stable across every request -- the precondition for caching it.
     */
    private String buildSystemPrompt(SearchContract searchContract) {
        return "# The search vocabulary\n\n"
                + searchContract.asPromptSection()
                + "\n# How to read a query\n\n"
                + readResource("prompt/query-parser.md");
    }

    private static String readResource(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompt resource " + path, e);
        }
    }
}
