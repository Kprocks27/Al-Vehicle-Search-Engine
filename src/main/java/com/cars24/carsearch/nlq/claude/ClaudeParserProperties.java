package com.cars24.carsearch.nlq.claude;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Knobs for the Claude-backed parser.
 *
 * @param model    model id, e.g. claude-opus-5
 * @param effort   how hard the model thinks. Leave blank to omit the parameter entirely -- some
 *                 models reject it outright (Haiku 4.5 among them), so "unset" has to be a real
 *                 option rather than a default that quietly breaks a model switch.
 * @param maxTokens ceiling on the response. The output here is a small JSON object; this exists to
 *                  bound a runaway, not to shape the answer.
 * @param timeout  ceiling per attempt, not per search. The SDK retries a timed-out attempt twice by
 *                 default, so a search can wait about three times this, plus backoff, before the
 *                 parser gives up. The SDK default is ten minutes, which is the right default for
 *                 long generations and the wrong one for a search box.
 */
@ConfigurationProperties(prefix = "carsearch.parser.claude")
public record ClaudeParserProperties(String model, String effort, Long maxTokens, Duration timeout) {

    public ClaudeParserProperties {
        model = (model == null || model.isBlank()) ? "claude-opus-5" : model.trim();
        maxTokens = maxTokens == null ? 2048L : maxTokens;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    public boolean hasEffort() {
        return effort != null && !effort.isBlank();
    }
}
