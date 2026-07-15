// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.componenttest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FetchUtils {
    private FetchUtils() {}

    private static final Logger log = LoggerFactory.getLogger(FetchUtils.class);

    /** Maximum number of attempts to fetch a resource. */
    private static final int MAX_ATTEMPTS = 5;
    /** Cap how long we wait for a single 429 Retry-After delay. */
    private static final long MAX_RETRY_AFTER_MS = 5_000L;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * HTTP helper that retries on 429 responses, respecting Retry-After when present.
     */
    static HttpResponse<String> fetchWithRetry(String method, String url, String contentType, String body)
            throws Exception {
        var attempts = 0;
        while (attempts < MAX_ATTEMPTS) {
            // inject a small amount of latency to reduce 429s
            Thread.sleep(10);
            var builder = HttpRequest.newBuilder().uri(URI.create(url));
            TestConfig.authHeaders().forEach(builder::header);
            if (contentType != null) {
                builder.header("Content-Type", contentType);
            }
            builder.method(method, body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody());

            var response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                attempts++;
                var delayMs = retryDelayMs(response, attempts);
                log.warn("Rate limited, retrying in {}ms... {}/{}", delayMs, attempts, MAX_ATTEMPTS);
                Thread.sleep(delayMs);
                continue;
            }
            return response;
        }
        throw new RuntimeException("Failed to fetch with retry");
    }

    /**
     * Prefer the server's Retry-After (delta-seconds or HTTP-date), capped at
     * {@link #MAX_RETRY_AFTER_MS}. Falls back to linear backoff when absent/invalid.
     */
    private static long retryDelayMs(HttpResponse<?> response, int attempt) {
        var retryAfter = response.headers().firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            var value = retryAfter.get().trim();
            try {
                long seconds = Long.parseLong(value);
                return Math.min(Math.max(0L, seconds) * 1000L, MAX_RETRY_AFTER_MS);
            } catch (NumberFormatException ignored) {
                try {
                    var retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    var delayMs = Math.max(0L, Duration.between(Instant.now(), retryAt).toMillis());
                    return Math.min(delayMs, MAX_RETRY_AFTER_MS);
                } catch (DateTimeParseException ignoredDate) {
                    log.warn("Ignoring unparsable Retry-After header: {}", value);
                }
            }
        }
        return attempt * 200L;
    }

    static HttpResponse<String> get(String url) throws Exception {
        return fetchWithRetry("GET", url, null, null);
    }

    static HttpResponse<String> postJson(String url, String body) throws Exception {
        return fetchWithRetry("POST", url, "application/json", body);
    }

    static HttpResponse<String> postYaml(String url, String body) throws Exception {
        return fetchWithRetry("POST", url, "application/x-yaml", body);
    }

    static HttpResponse<String> putJson(String url, String body) throws Exception {
        return fetchWithRetry("PUT", url, "application/json", body);
    }

    static HttpResponse<String> delete(String url) {
        try {
            return fetchWithRetry("DELETE", url, null, null);
        } catch (Exception e) {
            log.warn("DELETE {} failed: {}", url, e.getMessage());
            return null;
        }
    }
}
