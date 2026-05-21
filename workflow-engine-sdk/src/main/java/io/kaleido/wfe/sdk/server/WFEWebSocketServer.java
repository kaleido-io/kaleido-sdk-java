// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.server;

import io.kaleido.wfe.sdk.config.RuntimeConfig;
import io.kaleido.wfe.sdk.config.ServerConfig;
import io.kaleido.wfe.sdk.dispatch.WFEDispatcher;
import io.kaleido.wfe.sdk.errors.SDKErrors;
import io.kaleido.wfe.sdk.errors.SDKException;
import io.kaleido.wfe.sdk.handlers.*;
import io.kaleido.wfe.sdk.protocol.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.time.Duration;
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
    private final ThreadLocal<WFEServerEndpoint> currentEndpoint = new ThreadLocal<>();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { var t = new Thread(r, "wfe-server-heartbeat"); t.setDaemon(true); return t; });

    private Server jettyServer;

    public WFEWebSocketServer(RuntimeConfig runtimeConfig, ServerConfig serverConfig, HandlerSet handlerSet) {
        this.runtimeConfig = runtimeConfig;
        this.serverConfig = serverConfig;
        this.handlerSet = handlerSet;
        this.rateLimitTokens = serverConfig.burst() > 0 ? serverConfig.burst() : serverConfig.requestsPerSecond();
    }

    public void start() throws Exception {
        var handlerList = handlerSet.init(this);
        for (var h : handlerList) {
            handlers.put(h.name(), h);
        }

        jettyServer = new Server();
        ServerConnector connector;

        var tlsConfig = serverConfig.tls();
        if (tlsConfig != null && tlsConfig.enabled()) {
            var sslContextFactory = buildSslContextFactory(tlsConfig);
            connector = new ServerConnector(jettyServer, sslContextFactory);
        } else {
            connector = new ServerConnector(jettyServer);
        }

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
            String scheme = (tlsConfig != null && tlsConfig.enabled()) ? "wss" : "ws";
            log.info("WFE server listening on {}://{}:{}{}", scheme, serverConfig.address(),
                    serverConfig.port(), WS_PATH);
        } catch (Exception e) {
            throw SDKErrors.error(SDKErrors.SERVER_BIND_FAILED,
                    "Failed to start WFE server on port " + serverConfig.port(), e);
        }
    }

    private SslContextFactory.Server buildSslContextFactory(ServerConfig.TlsConfig tlsConfig) {
        if (tlsConfig.certFile() == null || tlsConfig.keyFile() == null) {
            throw SDKErrors.error(SDKErrors.SERVER_TLS_CONFIG_INVALID,
                    "TLS enabled but certFile or keyFile not specified");
        }

        try {
            var sslContextFactory = new SslContextFactory.Server();

            var keyStore = loadPemKeyStore(tlsConfig.certFile(), tlsConfig.keyFile());
            sslContextFactory.setKeyStore(keyStore);
            sslContextFactory.setKeyStorePassword("");

            if (tlsConfig.caFile() != null) {
                var trustStore = loadPemTrustStore(tlsConfig.caFile());
                sslContextFactory.setTrustStore(trustStore);
            }

            if (tlsConfig.clientAuth()) {
                sslContextFactory.setNeedClientAuth(true);
            }

            return sslContextFactory;
        } catch (SDKException e) {
            throw e;
        } catch (Exception e) {
            throw SDKErrors.error(SDKErrors.SERVER_TLS_CONFIG_INVALID,
                    "Failed to configure TLS: " + e.getMessage(), e);
        }
    }

    private static java.security.KeyStore loadPemKeyStore(String certFile, String keyFile) throws Exception {
        var certFactory = java.security.cert.CertificateFactory.getInstance("X.509");

        java.security.cert.Certificate[] chain;
        try (var certStream = java.nio.file.Files.newInputStream(java.nio.file.Path.of(certFile))) {
            chain = certFactory.generateCertificates(certStream).toArray(new java.security.cert.Certificate[0]);
        }

        java.security.PrivateKey privateKey;
        var keyBytes = java.nio.file.Files.readString(java.nio.file.Path.of(keyFile));
        var keyContent = keyBytes
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        var decoded = java.util.Base64.getDecoder().decode(keyContent);
        var keySpec = new java.security.spec.PKCS8EncodedKeySpec(decoded);

        try {
            privateKey = java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            try {
                privateKey = java.security.KeyFactory.getInstance("EC").generatePrivate(keySpec);
            } catch (Exception e2) {
                privateKey = java.security.KeyFactory.getInstance("Ed25519").generatePrivate(keySpec);
            }
        }

        var keyStore = java.security.KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", privateKey, "".toCharArray(), chain);
        return keyStore;
    }

    private static java.security.KeyStore loadPemTrustStore(String caFile) throws Exception {
        var certFactory = java.security.cert.CertificateFactory.getInstance("X.509");
        var trustStore = java.security.KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);

        try (var caStream = java.nio.file.Files.newInputStream(java.nio.file.Path.of(caFile))) {
            var certs = certFactory.generateCertificates(caStream);
            int i = 0;
            for (var cert : certs) {
                trustStore.setCertificateEntry("ca-" + i++, cert);
            }
        }
        return trustStore;
    }

    public void stop() {
        running.set(false);
        heartbeatScheduler.shutdownNow();
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

    private long rateLimitLastRefill = System.nanoTime();
    private double rateLimitTokens;

    private boolean tryAcquire() {
        if (serverConfig.requestsPerSecond() <= 0) return true;
        long now = System.nanoTime();
        synchronized (this) {
            long elapsed = now - rateLimitLastRefill;
            if (elapsed > 0) {
                double newTokens = elapsed * ((double) serverConfig.requestsPerSecond() / 1_000_000_000L);
                int maxBurst = serverConfig.burst() > 0 ? serverConfig.burst() : serverConfig.requestsPerSecond();
                rateLimitTokens = Math.min(maxBurst, rateLimitTokens + newTokens);
                rateLimitLastRefill = now;
            }
            if (rateLimitTokens >= 1.0) {
                rateLimitTokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    @WebSocket
    public class WFEServerEndpoint {
        private final ConcurrentHashMap<String, CompletableFuture<String>> inflightRequests = new ConcurrentHashMap<>();
        private final AtomicReference<String> activeRequestId = new AtomicReference<>();
        private final WFEDispatcher dispatcher = new WFEDispatcher(handlers, activeRequestId);
        private final ExecutorService dispatchExecutor = Executors.newVirtualThreadPerTaskExecutor();
        private volatile Session session;
        private volatile ScheduledFuture<?> heartbeatTask;

        @OnWebSocketOpen
        public void onOpen(Session session) {
            this.session = session;
            log.info("Engine connected from {}", session.getRemoteSocketAddress());
            WFEDispatcher.sendRegistration(
                    runtimeConfig.providerName(),
                    runtimeConfig.providerMetadata(),
                    handlers,
                    msg -> sendJson(session, msg));
            startHeartbeat(session);
        }

        private void startHeartbeat(Session session) {
            Duration interval = serverConfig.heartbeatInterval();
            long intervalMs = interval.toMillis();
            session.setIdleTimeout(Duration.ofMillis(intervalMs * 3));
            heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
                if (!session.isOpen()) return;
                session.sendPing(ByteBuffer.allocate(0), Callback.from(
                        () -> {},
                        ex -> log.debug("Server ping send failed: {}", ex.getMessage())));
            }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        }

        @OnWebSocketMessage
        public void onMessage(Session session, String text) {
            if (!tryAcquire()) {
                log.warn("Rate limit exceeded, rejecting request");
                sendJson(session, WSEnvelope.error("Rate limit exceeded"));
                return;
            }
            try {
                var envelope = JSON.MAPPER.readValue(text, WSEnvelope.class);
                if (envelope.messageType() == WSMessageType.ENGINE_API_SUBMIT_TRANSACTIONS_RESULT) {
                    var future = inflightRequests.remove(envelope.id());
                    if (future != null) {
                        future.complete(text);
                    }
                } else if (envelope.messageType() == WSMessageType.EVENT_SOURCE_CONFIG
                        || envelope.messageType() == WSMessageType.PROTOCOL_ERROR) {
                    currentEndpoint.set(this);
                    try {
                        dispatcher.dispatch(envelope, text, msg -> sendJson(session, msg));
                    } finally {
                        currentEndpoint.remove();
                    }
                } else {
                    dispatchExecutor.submit(() -> {
                        currentEndpoint.set(this);
                        try {
                            dispatcher.dispatch(envelope, text, msg -> sendJson(session, msg));
                        } finally {
                            currentEndpoint.remove();
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Error processing message", e);
            }
        }

        @OnWebSocketClose
        public void onClose(Session session, int statusCode, String reason) {
            log.info("Engine disconnected: {} {}", statusCode, reason);
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
            }
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
