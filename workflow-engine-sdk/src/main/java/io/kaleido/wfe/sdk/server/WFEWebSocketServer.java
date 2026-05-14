// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.server;

import io.kaleido.wfe.sdk.config.RuntimeConfig;
import io.kaleido.wfe.sdk.config.ServerConfig;
import io.kaleido.wfe.sdk.dispatch.WFEDispatcher;
import io.kaleido.wfe.sdk.errors.SDKErrors;
import io.kaleido.wfe.sdk.handlers.*;
import io.kaleido.wfe.sdk.protocol.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket server that accepts inbound connections from the Kaleido workflow
 * engine. Mirrors the wire protocol of {@link io.kaleido.wfe.sdk.client.WFEWebSocketClient}
 * but flipped: the engine dials the SDK rather than the SDK dialing the engine.
 *
 * <p>The upgrade path is hard-coded to {@code /ws} to match
 * {@code NewHandlerRuntimeServerWrapper} in the Go SDK -- see
 * {@code .cursor/plans/go-sdk.md} for the protocol source of truth. There is no
 * bearer-token / header auth at the WS upgrade; the supported identity boundary
 * is mTLS via {@link ServerConfig.TlsConfig#clientAuth()}.
 *
 * <p><strong>Round-robin warning:</strong> if multiple inbound connections
 * register the same {@code providerName}, the engine round-robins requests
 * across them (see {@code workflow-engine/pkg/wshandler/wsserver.go} --
 * {@code bindConnectionAndLock} + {@code wlmCounter}). Handlers should be
 * stateless or coordinate externally.
 */
public class WFEWebSocketServer implements EngineAPI, Closeable {

    /** Hard-coded by the Go SDK; not configurable. */
    public static final String WS_PATH = "/ws";

    private static final Logger log = LoggerFactory.getLogger(WFEWebSocketServer.class);

    private final RuntimeConfig runtimeConfig;
    private final ServerConfig serverConfig;
    private final HandlerSet handlerSet;
    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** Currently active per-thread endpoint -- used by {@link #submitAsyncTransactions}. */
    private final ThreadLocal<WFEServerEndpoint> currentEndpoint = new ThreadLocal<>();

    private Server jettyServer;

    public WFEWebSocketServer(RuntimeConfig runtimeConfig, ServerConfig serverConfig, HandlerSet handlerSet) {
        this.runtimeConfig = runtimeConfig;
        this.serverConfig = serverConfig;
        this.handlerSet = handlerSet;
    }

    public void start() throws Exception {
        var handlerList = handlerSet.init(this);
        for (var h : handlerList) {
            handlers.put(h.name(), h);
        }

        jettyServer = new Server();
        var connector = new ServerConnector(jettyServer);
        connector.setHost(serverConfig.address());
        connector.setPort(serverConfig.port());
        jettyServer.addConnector(connector);

        var wsHandler = WebSocketUpgradeHandler.from(jettyServer, container -> {
            if (serverConfig.readBufferSize() > 0) {
                container.setInputBufferSize(serverConfig.readBufferSize());
            }
            if (serverConfig.writeBufferSize() > 0) {
                container.setOutputBufferSize(serverConfig.writeBufferSize());
            }
            container.addMapping(WS_PATH, (req, resp, cb) -> new WFEServerEndpoint());
        });
        jettyServer.setHandler(wsHandler);

        try {
            jettyServer.start();
            running.set(true);
            log.info("WFE server listening on {}:{}{}", serverConfig.address(),
                    serverConfig.port(), WS_PATH);
        } catch (Exception e) {
            throw SDKErrors.error(SDKErrors.SERVER_BIND_FAILED,
                    "Failed to start WFE server on port " + serverConfig.port(), e);
        }
    }

    public void stop() {
        running.set(false);
        for (var h : handlers.values()) {
            try {
                h.close();
            } catch (Exception e) {
                log.warn("Error closing handler {}: {}", h.name(), e.getMessage());
            }
        }
        handlerSet.close();
        if (jettyServer != null) {
            try {
                jettyServer.stop();
            } catch (Exception e) {
                log.warn("Error stopping Jetty server: {}", e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        if (jettyServer != null) {
            var connector = (ServerConnector) jettyServer.getConnectors()[0];
            return connector.getLocalPort();
        }
        return serverConfig.port();
    }

    /**
     * Submits async transactions back over the same WebSocket connection that
     * delivered the current handler invocation. Must be called from within a
     * handler callback; outside that scope the per-connection endpoint is
     * unknown and the call fails with {@link SDKErrors#WS_SEND_FAILED}.
     */
    @Override
    public CompletableFuture<WSEngineAPISubmitTransactionsResult> submitAsyncTransactions(
            String authRef, List<AsyncTransactionInput> transactions) {
        var endpoint = currentEndpoint.get();
        if (endpoint == null) {
            return CompletableFuture.failedFuture(
                    SDKErrors.error(SDKErrors.WS_SEND_FAILED,
                            "submitAsyncTransactions must be called from a handler invocation"));
        }
        return endpoint.submitAsyncTransactions(authRef, transactions);
    }

    private static void sendJson(Session session, Object message) {
        try {
            var json = JSON.MAPPER.writeValueAsString(message);
            session.sendText(json, Callback.NOOP);
        } catch (Exception e) {
            log.error("Failed to send WS message", e);
        }
    }

    @WebSocket
    public class WFEServerEndpoint {
        private final StringBuilder buffer = new StringBuilder();
        private final ConcurrentHashMap<String, CompletableFuture<String>> inflightRequests = new ConcurrentHashMap<>();
        private final AtomicReference<String> activeRequestId = new AtomicReference<>();
        private final WFEDispatcher dispatcher = new WFEDispatcher(handlers, activeRequestId);
        private volatile Session session;

        @OnWebSocketOpen
        public void onOpen(Session session) {
            this.session = session;
            log.info("Engine connected from {}", session.getRemoteSocketAddress());
            WFEDispatcher.sendRegistration(
                    runtimeConfig.providerName(),
                    runtimeConfig.providerMetadata(),
                    handlers,
                    msg -> sendJson(session, msg));
        }

        @OnWebSocketMessage
        public void onMessage(Session session, String text) {
            currentEndpoint.set(this);
            try {
                var envelope = JSON.MAPPER.readValue(text, WSEnvelope.class);
                if (envelope.messageType() == WSMessageType.ENGINE_API_SUBMIT_TRANSACTIONS_RESULT) {
                    var future = inflightRequests.remove(envelope.id());
                    if (future != null) {
                        future.complete(text);
                    }
                } else {
                    dispatcher.dispatch(envelope, text, msg -> sendJson(session, msg));
                }
            } catch (Exception e) {
                log.error("Error processing message", e);
            } finally {
                currentEndpoint.remove();
            }
        }

        @OnWebSocketClose
        public void onClose(Session session, int statusCode, String reason) {
            log.info("Engine disconnected: {} {}", statusCode, reason);
            inflightRequests.values().forEach(f -> f.completeExceptionally(
                    SDKErrors.error(SDKErrors.WS_SEND_FAILED, "WebSocket closed")));
            inflightRequests.clear();
        }

        @OnWebSocketError
        public void onError(Session session, Throwable error) {
            log.error("WS error: {}", error.getMessage());
        }

        CompletableFuture<WSEngineAPISubmitTransactionsResult> submitAsyncTransactions(
                String authRef, List<AsyncTransactionInput> transactions) {
            var s = this.session;
            if (s == null || !s.isOpen()) {
                return CompletableFuture.failedFuture(
                        SDKErrors.error(SDKErrors.WS_SEND_FAILED, "No active session"));
            }

            var id = java.util.UUID.randomUUID().toString();
            var currentRequestId = activeRequestId.get();
            var request = WSEngineAPISubmitTransactions.of(id, currentRequestId, authRef, transactions);
            var future = new CompletableFuture<String>();
            inflightRequests.put(id, future);

            try {
                var json = JSON.MAPPER.writeValueAsString(request);
                s.sendText(json, Callback.NOOP);
            } catch (Exception e) {
                inflightRequests.remove(id);
                return CompletableFuture.failedFuture(
                        SDKErrors.error(SDKErrors.WS_SEND_FAILED, "Failed to send", e));
            }

            var timeout = runtimeConfig.resultTimeout() != null
                    ? runtimeConfig.resultTimeout()
                    : java.time.Duration.ofMinutes(2);
            return future
                    .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .thenApply(responseJson -> {
                        try {
                            return JSON.MAPPER.readValue(responseJson, WSEngineAPISubmitTransactionsResult.class);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    })
                    .whenComplete((result, ex) -> inflightRequests.remove(id));
        }
    }
}
