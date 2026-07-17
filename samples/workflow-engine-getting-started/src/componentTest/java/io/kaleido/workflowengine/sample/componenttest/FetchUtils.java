// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample.componenttest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class FetchUtils {
    private FetchUtils() {}

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static HttpResponse<String> send(String method, String url, String contentType, String body)
            throws Exception {
        var builder = HttpRequest.newBuilder().uri(URI.create(url));
        TestConfig.authHeaders().forEach(builder::header);
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        builder.method(method, body != null
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody());
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> get(String url) throws Exception {
        return send("GET", url, null, null);
    }

    static HttpResponse<String> postJson(String url, String body) throws Exception {
        return send("POST", url, "application/json", body);
    }

    static HttpResponse<String> postYaml(String url, String body) throws Exception {
        return send("POST", url, "application/x-yaml", body);
    }

    static HttpResponse<String> putJson(String url, String body) throws Exception {
        return send("PUT", url, "application/json", body);
    }

    static HttpResponse<String> delete(String url) {
        try {
            return send("DELETE", url, null, null);
        } catch (Exception e) {
            return null;
        }
    }
}
