// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kaleido.wfe.sdk.config.AuthConfig;
import io.kaleido.wfe.sdk.config.RuntimeConfig;
import io.kaleido.wfe.sdk.dispatch.WFEDispatcher;
import io.kaleido.wfe.sdk.errors.SDKErrors;
import io.kaleido.wfe.sdk.handlers.*;
import io.kaleido.wfe.sdk.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class WFEWebSocketClient implements EngineAPI, Closeable {

    private static final Logger log = LoggerFactory.getLogger(WFEWebSocketClient.class);

    private final RuntimeConfig config;
    private final HandlerSet handlerSet;
    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final CompletableFuture<Void> stoppedFuture = new CompletableFuture<>();

    private final ConcurrentHashMap<String, CompletableFuture<String>> inflightRequests = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeRequestId = new AtomicReference<>();

    private volatile WebSocket webSocket;
    private WFEDispatcher dispatcher;
    private final ExecutorService dispatchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "wfe-scheduler");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> heartbeatTask;
    private final AtomicLong lastPong = new AtomicLong(System.currentTimeMillis());

    public WFEWebSocketClient(RuntimeConfig config, HandlerSet handlerSet) {
        this.config = config;
        this.handlerSet = handlerSet;
    }

    public CompletableFuture<Void> connect() {
        if (config.url() == null) {
            return CompletableFuture.failedFuture(
                    SDKErrors.error(SDKErrors.WS_CONNECT_FAILED, "No URL configured for WFE client"));
        }
        var handlerList = handlerSet.init(this);
        for (var h : handlerList) {
            handlers.put(h.name(), h);
        }
        this.dispatcher = new WFEDispatcher(handlers, activeRequestId);
        return connectWithRetry(0);
    }

    private CompletableFuture<Void> connectWithRetry(int attempt) {
        if (stopped.get()) {
            return CompletableFuture.completedFuture(null);
        }

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        var wsBuilder = httpClient.newWebSocketBuilder();
        applyAuth(wsBuilder);
        if (config.extraHeaders() != null) {
            config.extraHeaders().forEach(wsBuilder::header);
        }

        return wsBuilder.buildAsync(config.url(), new WFEListener())
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    log.info("WebSocket connected to {}", config.url());
                    registerProviderAndHandlers(ws);
                    startHeartbeat();
                    connected.set(true);
                })
                .exceptionally(ex -> {
                    log.warn("WebSocket connection failed (attempt {}): {}", attempt, ex.getMessage());
                    if (shouldReconnect.get() && !stopped.get()) {
                        int max = config.maxAttempts();
                        if (max > 0 && attempt + 1 >= max) {
                            log.error("Max connection attempts ({}) reached, giving up", max);
                            stoppedFuture.complete(null);
                            return null;
                        }
                        long delay = computeBackoff(attempt);
                        log.info("Reconnecting in {}ms", delay);
                        scheduler.schedule(() -> connectWithRetry(attempt + 1), delay, TimeUnit.MILLISECONDS);
                    }
                    return null;
                });
    }

    private void applyAuth(WebSocket.Builder wsBuilder) {
        if (config.auth() == null) return;

        if (config.auth() instanceof AuthConfig.CustomHeaders custom) {
            custom.headers().forEach(wsBuilder::header);
        } else {
            var name = config.auth().resolveHeaderName();
            var value = config.auth().resolveHeaderValue();
            if (name != null && value != null) {
                wsBuilder.header(name, value);
            }
        }
    }

    private void registerProviderAndHandlers(WebSocket ws) {
        WFEDispatcher.sendRegistration(config.providerName(), config.providerMetadata(),
                handlers, msg -> sendJson(ws, msg));
    }

    private void startHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        lastPong.set(System.currentTimeMillis());
        var interval = config.heartbeatInterval() != null ? config.heartbeatInterval() : Duration.ofSeconds(30);
        var pongTimeout = config.pongTimeout() != null ? config.pongTimeout() : Duration.ofSeconds(10);
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            var ws = this.webSocket;
            if (ws == null) return;
            long elapsed = System.currentTimeMillis() - lastPong.get();
            if (elapsed > pongTimeout.toMillis() + interval.toMillis()) {
                log.warn("Pong timeout ({}ms), forcing reconnect", elapsed);
                ws.abort();
                return;
            }
            ws.sendPing(ByteBuffer.allocate(0)).exceptionally(ex -> {
                log.debug("Ping send failed: {}", ex.getMessage());
                return null;
            });
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void handleMessage(String json) {
        try {
            var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
            if (envelope.messageType() == null) {
                log.warn("Received message with no messageType");
                return;
            }

            switch (envelope.messageType()) {
                case ENGINE_API_SUBMIT_TRANSACTIONS_RESULT -> completeInflight(envelope.id(), json);
                // EVENT_SOURCE_CONFIG MUST run synchronously so it is visible to the
                // next poll for the same stream (Go invariant; see WFEDispatcher).
                case EVENT_SOURCE_CONFIG, PROTOCOL_ERROR ->
                        dispatcher.dispatch(envelope, json, msg -> sendJson(webSocket, msg));
                default -> dispatchExecutor.submit(() ->
                        dispatcher.dispatch(envelope, json, msg -> sendJson(webSocket, msg)));
            }
        } catch (Exception e) {
            log.error("Failed to parse WS message", e);
        }
    }

    @Override
    public CompletableFuture<WSEngineAPISubmitTransactionsResult> submitAsyncTransactions(
            String authRef, List<AsyncTransactionInput> transactions) {
        var id = UUID.randomUUID().toString();
        var currentRequestId = activeRequestId.get();

        var request = WSEngineAPISubmitTransactions.of(id, currentRequestId, authRef, transactions);
        var future = new CompletableFuture<String>();
        inflightRequests.put(id, future);

        sendJson(webSocket, request);

        var timeout = config.resultTimeout() != null ? config.resultTimeout() : Duration.ofMinutes(2);
        return future
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(responseJson -> {
                    try {
                        return JSON.MAPPER.readValue(responseJson, WSEngineAPISubmitTransactionsResult.class);
                    } catch (JsonProcessingException e) {
                        throw new CompletionException(e);
                    }
                })
                .whenComplete((result, ex) -> inflightRequests.remove(id));
    }

    private void completeInflight(String id, String json) {
        var future = inflightRequests.remove(id);
        if (future != null) {
            future.complete(json);
        }
    }

    private void sendJson(WebSocket ws, Object message) {
        if (ws == null) return;
        try {
            var json = JSON.MAPPER.writeValueAsString(message);
            ws.sendText(json, true).join();
        } catch (Exception e) {
            log.error("Failed to send WS message", e);
        }
    }

    private long computeBackoff(int attempt) {
        var delay = config.reconnectDelay() != null ? config.reconnectDelay() : Duration.ofSeconds(1);
        var maxDelay = config.maxReconnectDelay() != null ? config.maxReconnectDelay() : Duration.ofSeconds(30);
        var factor = config.reconnectDelayFactor() > 0 ? config.reconnectDelayFactor() : 2.0;
        long ms = (long) (delay.toMillis() * Math.pow(factor, attempt));
        return Math.min(ms, maxDelay.toMillis());
    }

    private void onDisconnect() {
        connected.set(false);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        inflightRequests.values().forEach(f ->
                f.completeExceptionally(SDKErrors.error(SDKErrors.WS_CONNECT_FAILED, "WebSocket closed")));
        inflightRequests.clear();

        if (shouldReconnect.get() && !stopped.get()) {
            log.info("Connection lost, reconnecting...");
            connectWithRetry(0);
        } else {
            stoppedFuture.complete(null);
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void waitStopped() throws InterruptedException {
        try {
            stoppedFuture.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        stopped.set(true);
        shouldReconnect.set(false);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        var ws = this.webSocket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").join();
        }
        for (var h : handlers.values()) {
            try {
                h.close();
            } catch (Exception e) {
                log.warn("Error closing handler {}: {}", h.name(), e.getMessage());
            }
        }
        handlerSet.close();
        dispatchExecutor.shutdown();
        scheduler.shutdown();
        stoppedFuture.complete(null);
    }

    @Override
    public void close() {
        stop();
    }

    private class WFEListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            log.debug("WS onOpen");
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                var message = buffer.toString();
                buffer.setLength(0);
                handleMessage(message);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket ws, ByteBuffer message) {
            lastPong.set(System.currentTimeMillis());
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.info("WS closed: {} {}", statusCode, reason);
            onDisconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.error("WS error: {}", error.getMessage());
            onDisconnect();
        }
    }
}
