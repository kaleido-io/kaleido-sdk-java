// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.componenttest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP helper that retries on 429 responses, so the tests run robustly against
 * extra-small workflow engine runtimes.
 */
final class FetchUtils {
    private FetchUtils() {}

    private static final Logger log = LoggerFactory.getLogger(FetchUtils.class);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    static HttpResponse<String> fetchWithRetry(String method, String url, String contentType, String body)
            throws Exception {
        var attempts = 0;
        while (attempts < 5) {
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
                log.warn("Rate limited, retrying... {}/5", attempts);
                Thread.sleep(attempts * 200L);
                continue;
            }
            return response;
        }
        throw new RuntimeException("Failed to fetch with retry");
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
