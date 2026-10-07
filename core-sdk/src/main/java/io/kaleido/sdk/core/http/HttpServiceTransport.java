// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import io.kaleido.sdk.core.JSON;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Sends requests straight to the options' URL with their auth, retrying up to
 * {@code maxRetries}: a 429 or a connection that never opened, and for idempotent methods
 * also a 5xx or no response. A POST or PATCH that may have reached the service is not sent
 * again.
 */
final class HttpServiceTransport implements ServiceTransport {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration FIRST_RETRY_DELAY = Duration.ofMillis(250);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(5);
    private static final Set<String> IDEMPOTENT = Set.of("GET", "HEAD", "OPTIONS", "PUT", "DELETE");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ServiceClientOptions.Http options;

    HttpServiceTransport(ServiceClientOptions.Http options) {
        this.options = options;
    }

    @Override
    public ServiceResponse send(String method, String path, Object body) {
        var request = request(method, path, body);
        var attempts = 1 + Math.max(0, options.maxRetries() != null ? options.maxRetries() : 0);
        var idempotent = IDEMPOTENT.contains(method.toUpperCase());
        var delay = FIRST_RETRY_DELAY;
        for (var attempt = 1; ; attempt++) {
            try {
                var response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                var status = response.statusCode();
                if (attempt >= attempts || !(status == 429 || (idempotent && status >= 500))) {
                    return new ServiceResponse(status, headers(response), response.body());
                }
            } catch (IOException e) {
                if (attempt >= attempts || !(idempotent || e instanceof ConnectException)) {
                    throw new ServiceClientException(method + " " + path + " failed: " + e.getMessage(), 0, null, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceClientException(method + " " + path + " interrupted", 0, null, e);
            }
            sleep(delay);
            delay = nextDelay(delay);
        }
    }

    /** Doubles the delay between attempts, up to {@code MAX_RETRY_DELAY}. */
    static Duration nextDelay(Duration delay) {
        var doubled = delay.multipliedBy(2);
        return doubled.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : doubled;
    }

    private HttpRequest request(String method, String path, Object body) {
        var timeout = options.timeout() != null ? Duration.ofMillis(options.timeout()) : DEFAULT_TIMEOUT;
        var builder = HttpRequest.newBuilder(URI.create(join(options.url(), path))).timeout(timeout);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(json(body)));
        }
        var auth = options.auth();
        if (auth != null && "token".equals(auth.type()) && auth.token() != null) {
            var header = auth.header() != null ? auth.header() : "Authorization";
            builder.header(header, auth.scheme() != null && !auth.scheme().isEmpty()
                    ? auth.scheme() + " " + auth.token() : auth.token());
        } else if (auth != null && auth.username() != null) {
            var credentials = auth.username() + ":" + (auth.password() != null ? auth.password() : "");
            builder.header("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return builder.build();
    }

    private static Map<String, String> headers(HttpResponse<?> response) {
        var out = new LinkedHashMap<String, String>();
        response.headers().map().forEach((name, values) -> out.put(name, String.join(", ", values)));
        return out;
    }

    private static byte[] json(Object body) {
        try {
            return JSON.MAPPER.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new IllegalArgumentException("body cannot be written as JSON: " + e.getMessage(), e);
        }
    }

    private static String join(String base, String path) {
        return base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceClientException("interrupted while retrying", 0, null, e);
        }
    }
}
