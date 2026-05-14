// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.wfe.sdk.config.RuntimeConfig;
import io.kaleido.wfe.sdk.errors.SDKErrors;
import io.kaleido.wfe.sdk.protocol.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WorkflowEngineRestClient {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineRestClient.class);

    private final HttpClient httpClient;
    private final URI baseUrl;
    private final String authToken;
    private final String authHeaderName;
    private final Map<String, String> extraHeaders;

    public WorkflowEngineRestClient(RuntimeConfig config) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.extraHeaders = config.extraHeaders() != null ? config.extraHeaders() : Map.of();

        if (config.url() != null) {
            this.baseUrl = URI.create(RuntimeConfig.wsUrlToRestUrl(config.url().toString()));
        } else {
            var account = System.getenv("ACCOUNT");
            var environment = System.getenv("ENVIRONMENT");
            var workflowEngine = System.getenv("WORKFLOW_ENGINE");
            if (account == null || account.isEmpty())
                throw SDKErrors.error(SDKErrors.ACCOUNT_NOT_SET, "ACCOUNT env var not set");
            if (environment == null || environment.isEmpty())
                throw SDKErrors.error(SDKErrors.ENVIRONMENT_NOT_SET, "ENVIRONMENT env var not set");
            if (workflowEngine == null || workflowEngine.isEmpty())
                throw SDKErrors.error(SDKErrors.WORKFLOW_ENGINE_NOT_SET, "WORKFLOW_ENGINE env var not set");
            this.baseUrl = URI.create("https://" + account + "/endpoint/" + environment + "/" + workflowEngine + "/rest");
        }

        // Resolve auth
        if (config.auth() != null) {
            this.authHeaderName = config.auth().resolveHeaderName();
            this.authToken = config.auth().resolveHeaderValue();
        } else {
            var keyName = System.getenv("KEY_NAME");
            var keyValue = System.getenv("KEY_VALUE");
            if (keyName != null && keyValue != null && !keyName.isEmpty()) {
                this.authHeaderName = "Authorization";
                this.authToken = "Basic " + Base64.getEncoder().encodeToString(
                        (keyName + ":" + keyValue).getBytes(StandardCharsets.UTF_8));
            } else {
                this.authHeaderName = null;
                this.authToken = null;
            }
        }
    }

    // --- Workflows ---

    public CompletableFuture<JsonNode> createWorkflow(JsonNode request) {
        return makeRequest(workflowsUrl(), "POST", request, null);
    }

    public CompletableFuture<JsonNode> createWorkflow(JsonNode request, Duration timeout) {
        return makeRequest(workflowsUrl(), "POST", request, timeout);
    }

    public CompletableFuture<Void> deleteWorkflow(String workflowNameOrId) {
        return makeRequest(workflowUrl(workflowNameOrId), "DELETE", null, null).thenApply(n -> null);
    }

    // --- Transactions ---

    public CompletableFuture<JsonNode> createTransaction(JsonNode request) {
        return makeRequest(transactionsUrl(), "POST", request, null);
    }

    public CompletableFuture<Void> deleteTransaction(String idempotencyKeyOrId) {
        return makeRequest(transactionUrl(idempotencyKeyOrId), "DELETE", null, null).thenApply(n -> null);
    }

    // --- Streams ---

    public CompletableFuture<JsonNode> createStream(JsonNode request) {
        return makeRequest(streamsUrl(), "POST", request, null);
    }

    public CompletableFuture<Void> deleteStream(String streamNameOrId, boolean force) {
        var url = streamUrl(streamNameOrId) + (force ? "?force=true" : "");
        return makeRequest(url, "DELETE", null, null).thenApply(n -> null);
    }

    public CompletableFuture<JsonNode> startStream(String streamNameOrId) {
        return makeRequest(streamUrl(streamNameOrId), "PATCH",
                JSON.MAPPER.createObjectNode().put("started", true), null);
    }

    public CompletableFuture<JsonNode> stopStream(String streamNameOrId) {
        return makeRequest(streamUrl(streamNameOrId), "PATCH",
                JSON.MAPPER.createObjectNode().put("started", false), null);
    }

    // --- URL builders ---

    private String workflowsUrl() { return baseUrl + "/api/v1/workflows"; }
    private String workflowUrl(String id) { return workflowsUrl() + "/" + enc(id); }
    private String transactionsUrl() { return baseUrl + "/api/v1/transactions"; }
    private String transactionUrl(String id) { return transactionsUrl() + "/" + enc(id); }
    private String streamsUrl() { return baseUrl + "/api/v1/streams"; }
    private String streamUrl(String id) { return streamsUrl() + "/" + enc(id); }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // --- HTTP ---

    private CompletableFuture<JsonNode> makeRequest(String url, String method, JsonNode body, Duration timeout) {
        try {
            var reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");

            if (authToken != null && authHeaderName != null) {
                reqBuilder.header(authHeaderName, authToken);
            }
            extraHeaders.forEach(reqBuilder::header);

            if (timeout != null) {
                long minutes = timeout.toMinutes();
                reqBuilder.header("Request-Timeout", minutes + "m0s");
            }

            var bodyPublisher = body != null
                    ? HttpRequest.BodyPublishers.ofString(JSON.MAPPER.writeValueAsString(body))
                    : HttpRequest.BodyPublishers.noBody();

            var request = reqBuilder.method(method, bodyPublisher).build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 204) {
                            return null;
                        }
                        if (response.statusCode() >= 400) {
                            throw SDKErrors.error(SDKErrors.REST_REQUEST_FAILED,
                                    method + " " + url + " returned " + response.statusCode() + ": " + response.body());
                        }
                        try {
                            return JSON.MAPPER.readTree(response.body());
                        } catch (Exception e) {
                            throw SDKErrors.error(SDKErrors.REST_REQUEST_FAILED,
                                    "Failed to parse response from " + url, e);
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    SDKErrors.error(SDKErrors.REST_REQUEST_FAILED, "Request failed: " + url, e));
        }
    }
}
