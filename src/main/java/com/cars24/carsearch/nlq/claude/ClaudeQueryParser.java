package com.cars24.carsearch.nlq.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.cars24.carsearch.nlq.ParsedFilter;
import com.cars24.carsearch.nlq.ParsedQuery;
import com.cars24.carsearch.nlq.ParserUnavailableException;
import com.cars24.carsearch.nlq.QueryParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * The real parser: one Claude call per uncached query, up to three when the SDK retries a failure,
 * constrained to a JSON schema.
 *
 * <p>Note what this class does not have: no repository, no EntityManager, no connection. The
 * {@link QueryParser} interface hands it none and it takes none; that is a rule kept by whoever
 * writes the code, not something the compiler enforces. It returns a
 * {@code ParsedQuery} of plain strings that the validator then takes apart claim by claim. Every
 * guarantee downstream of here was built and tested against the stub, so this implementation
 * inherits a pipeline that already assumes it will sometimes be wrong.
 *
 * <p>The system prompt is assembled once at startup and is byte-identical on every request, which
 * is what makes the cache breakpoint at the end of it worth having.
 */
public class ClaudeQueryParser implements QueryParser {

    private static final Logger log = LoggerFactory.getLogger(ClaudeQueryParser.class);

    private final AnthropicClient client;
    private final ClaudeParserProperties properties;
    private final String systemPrompt;
    private final ObjectMapper objectMapper;

    public ClaudeQueryParser(AnthropicClient client,
                             ClaudeParserProperties properties,
                             String systemPrompt,
                             ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.systemPrompt = systemPrompt;
        this.objectMapper = objectMapper;
    }

    @Override
    public ParsedQuery parse(String query) {
        Message response;
        try {
            response = client.messages().create(request(query));
        } catch (AnthropicException e) {
            // The interpretation layer is down, unreachable, timed out or refusing. The base class
            // covers HTTP errors and network failures alike, once the SDK's own retries are spent.
            // That is not the shopper's problem and not something a different sentence would fix,
            // so it must not surface as "we did not understand you".
            throw new ParserUnavailableException("Claude call failed: " + e.getMessage(), e);
        }

        // On current models a refusal is an HTTP 200 with a stop reason, not an exception. Reading
        // content without checking gets you an empty or partial parse that looks like a bad query.
        if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new ParserUnavailableException(
                    "Claude declined to interpret the query: "
                            + response.stopDetails().map(Object::toString).orElse("no detail"));
        }

        String json = textOf(response);
        // The raw body, before anything touches it. When a query comes back wrong, the first
        // question is always whether the model said something odd or we mishandled something
        // ordinary, and this line answers it without a debugger.
        log.debug("Claude raw output for '{}': {}", query, json);

        try {
            LlmQuery parsed = objectMapper.readValue(json, LlmQuery.class);
            log.debug("Claude returned {} filter(s) and {} feature(s); cache_read={} in={} out={}",
                    parsed.filters().size(), parsed.features().size(),
                    response.usage().cacheReadInputTokens().orElse(0L),
                    response.usage().inputTokens(), response.usage().outputTokens());
            return toParsedQuery(parsed);
        } catch (Exception e) {
            throw new ParserUnavailableException(
                    "Claude returned output that did not match the schema: " + json, e);
        }
    }

    private MessageCreateParams request(String query) {
        OutputConfig.Builder outputConfig = OutputConfig.builder().format(QuerySchema.build());
        if (properties.hasEffort()) {
            outputConfig.effort(OutputConfig.Effort.of(properties.effort().toLowerCase(Locale.ROOT)));
        }

        return MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .outputConfig(outputConfig.build())
                // The whole prompt is stable, so the breakpoint goes at its end: every request
                // after the first reads the contract rather than re-sending it as fresh input.
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(systemPrompt)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(query)
                .build();
    }

    private static String textOf(Message response) {
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .reduce("", String::concat)
                .trim();

        if (text.isEmpty()) {
            throw new ParserUnavailableException("Claude returned no text content");
        }
        return text;
    }

    /**
     * Down to the untrusted shape the rest of the pipeline expects. Nothing is checked here --
     * checking is the validator's job, and doing any of it twice means two places to keep in step.
     */
    private static ParsedQuery toParsedQuery(LlmQuery parsed) {
        return new ParsedQuery(
                parsed.filters().stream()
                        .map(f -> new ParsedFilter(f.field(), f.comparison(), f.values()))
                        .toList(),
                parsed.features());
    }
}
