// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.WSMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * WebSocket service proxy adapter.
 *
 * <p>Correlates outgoing {@link ServiceProxyRequest} messages with incoming
 * {@link ServiceProxyResponse} messages, using the runtime's existing
 * WebSocket connection as the transport.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>Created by the handler runtime in its constructor</li>
 *   <li>Wired via {@link #setRuntime} so it can send on the runtime's WebSocket</li>
 *   <li>SERVICE_PROXY_RESPONSE messages are routed here by the runtime's message loop</li>
 * </ul>
 */
public class WSProxyAdapter {

    private static final Logger log = LoggerFactory.getLogger(WSProxyAdapter.class);

    public static final long DEFAULT_REQUEST_TIMEOUT_MS = 120_000;

    private volatile ProxyAdapterRuntime runtime;
    private final long requestTimeoutMs;
    private final ConcurrentHashMap<String, CompletableFuture<ServiceProxyResponse>> inflightRequests =
            new ConcurrentHashMap<>();

    public WSProxyAdapter() {
        this(DEFAULT_REQUEST_TIMEOUT_MS);
    }

    public WSProxyAdapter(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    /**
     * Bind to the handler runtime that owns the WebSocket connection.
     */
    public void setRuntime(ProxyAdapterRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Handle an incoming SERVICE_PROXY_RESPONSE. Called by the runtime's
     * message loop when a response arrives on the shared WebSocket connection.
     */
    public void handleResponse(ServiceProxyResponse response) {
        var inflight = inflightRequests.remove(response.requestId());
        if (inflight == null) {
            log.warn("Received proxy response for unknown request: {}", response.requestId());
            return;
        }
        if (response.error() != null && (response.status() == 0 || response.status() >= 400)) {
            inflight.completeExceptionally(new RuntimeException("Service proxy error: " + response.error()));
        } else {
            inflight.complete(response);
        }
    }

    /**
     * Send a service proxy request over the runtime's WebSocket, blocking until
     * the response arrives. The provider-proxy on the other end intercepts these
     * and makes the actual HTTP call with managed auth credentials.
     *
     * <p>If the WebSocket is not yet connected (e.g. setup hooks running just
     * after connect), waits up to the request timeout for it to become ready.
     */
    public ServiceProxyResponse request(
            String serviceType,
            String method,
            String id,
            Object body,
            Map<String, String> headers,
            String path,
            String authRef) throws Exception {

        waitForConnection(requestTimeoutMs);

        var requestId = UUID.randomUUID().toString();
        String bodyBase64 = null;
        if (body != null) {
            var bodyJson = body instanceof JsonNode node
                    ? JSON.MAPPER.writeValueAsBytes(node)
                    : JSON.MAPPER.writeValueAsBytes(body);
            bodyBase64 = Base64.getEncoder().encodeToString(bodyJson);
        }

        var message = new ServiceProxyRequest(
                WSMessageType.SERVICE_PROXY_REQUEST,
                requestId,
                serviceType,
                id,
                authRef,
                null,
                new ServiceProxyRequest.HttpRequestSpec(method, path, headers, null, bodyBase64));

        var future = new CompletableFuture<ServiceProxyResponse>();
        inflightRequests.put(requestId, future);
        try {
            runtime.sendMessage(message);
            return future.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("Service proxy request timed out after " + requestTimeoutMs + "ms");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        } finally {
            inflightRequests.remove(requestId);
        }
    }

    /**
     * Cancel all in-flight requests (e.g. on disconnect).
     */
    public void cancelAll() {
        var pending = inflightRequests.values().toArray(new CompletableFuture[0]);
        inflightRequests.clear();
        for (var future : pending) {
            future.completeExceptionally(new RuntimeException("WSProxyAdapter: connection closed"));
        }
    }

    /**
     * Wait until the runtime WebSocket is connected, polling every 100ms.
     * Tolerates the small window between the WebSocket open event and setup
     * hooks running (e.g. during provider startup or reconnection).
     */
    private void waitForConnection(long timeoutMs) throws InterruptedException {
        var deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            var currentRuntime = runtime;
            if (currentRuntime != null && currentRuntime.isWebSocketConnected()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("WSProxyAdapter: runtime WebSocket not connected");
    }
}
