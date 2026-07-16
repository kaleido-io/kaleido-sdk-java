// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import io.kaleido.workflowengine.sdk.config.AuthConfig;
import io.kaleido.workflowengine.sdk.config.ClientConfig;
import io.kaleido.workflowengine.sdk.config.ServerConfig;
import io.kaleido.workflowengine.sdk.handlers.CancellationSignal;
import io.kaleido.workflowengine.sdk.handlers.EventProcessor;
import io.kaleido.workflowengine.sdk.handlers.EventSource;
import io.kaleido.workflowengine.sdk.handlers.Handler;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.handlers.TransactionHandler;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.ProviderCapabilities;
import io.kaleido.workflowengine.sdk.protocol.WSEnvelope;
import io.kaleido.workflowengine.sdk.protocol.WSEvaluateReplyResult;
import io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchResult;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceConfig;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceDeleteRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceDeleteResult;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceValidateConfigRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceValidateConfigResult;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;
import io.kaleido.workflowengine.sdk.protocol.WSHandlerEnvelope;
import io.kaleido.workflowengine.sdk.protocol.WSHandlerType;
import io.kaleido.workflowengine.sdk.protocol.WSListenerPollRequest;
import io.kaleido.workflowengine.sdk.protocol.WSListenerPollResult;
import io.kaleido.workflowengine.sdk.protocol.WSRegisterHandler;
import io.kaleido.workflowengine.sdk.protocol.WSRegisterProvider;
import io.kaleido.workflowengine.sdk.protocol.WSSetupTriggerRequest;
import io.kaleido.workflowengine.sdk.protocol.WSSetupTriggerResponse;
import io.kaleido.workflowengine.sdk.service.ProxyAdapterRuntime;
import io.kaleido.workflowengine.sdk.service.ServiceProxyResponse;
import io.kaleido.workflowengine.sdk.service.WSProxyAdapter;
import org.java_websocket.handshake.ClientHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Internal runtime that manages the WebSocket connection to the workflow
 * engine and dispatches messages to registered handlers. Application code
 * normally uses {@code WorkflowEngineClient} rather than this class directly.
 */
public class HandlerRuntime implements ProxyAdapterRuntime, Closeable {

    private static final Logger log = LoggerFactory.getLogger(HandlerRuntime.class);

    private final ClientConfig config;

    private final Map<String, TransactionHandler> transactionHandlers = new ConcurrentHashMap<>();
    private final Map<String, EventSource> eventSources = new ConcurrentHashMap<>();
    private final Map<String, EventProcessor> eventProcessors = new ConcurrentHashMap<>();
    private final Map<String, WSEventSourceConfig> eventSourceConfigs = new ConcurrentHashMap<>();

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final CompletableFuture<Void> stoppedFuture = new CompletableFuture<>();

    private volatile WebSocket webSocket;
    /** Inbound mode: the accepted connection from the engine/provider-proxy dialing in. */
    private volatile org.java_websocket.WebSocket inboundConnection;
    /** Inbound mode: the listening server; null in outbound mode. */
    private volatile org.java_websocket.server.WebSocketServer wsServer;
    private final ExecutorService dispatchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "workflow-engine-scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> heartbeatTask;
    private final AtomicLong lastPong = new AtomicLong(System.currentTimeMillis());

    private final EngineClient engineClient;
    private final WSProxyAdapter wsProxyAdapter;

    /**
     * Optional handler invoked when a SETUP_TRIGGER_REQUEST arrives. Registered
     * by WorkflowEngineClient; absent during low-level direct-runtime usage
     * (which has no setup() lifecycle of its own).
     */
    private volatile SetupTriggerHandler setupTriggerHandler;

    /**
     * Per-provider capabilities declared on REGISTER_PROVIDER. Set by
     * WorkflowEngineClient just before connect(), based on inspecting the
     * actual handlers the customer registered. Direct-runtime users leave this
     * null — the platform then treats each capability as its conservative
     * default.
     */
    private volatile ProviderCapabilities providerCapabilities;

    public HandlerRuntime(ClientConfig config) {
        this.config = config;
        this.engineClient = new EngineClient(this);
        this.wsProxyAdapter = new WSProxyAdapter();
        this.wsProxyAdapter.setRuntime(this);
    }

    // ── Registration ────────────────────────────────────────────────────────

    public void registerTransactionHandler(String name, TransactionHandler handler) {
        transactionHandlers.put(name, handler);
    }

    public void registerEventSource(String name, EventSource handler) {
        eventSources.put(name, handler);
    }

    public void registerEventProcessor(String name, EventProcessor handler) {
        eventProcessors.put(name, handler);
    }

    /**
     * Register a callback invoked when the provider-proxy dispatches a setup
     * trigger. When no callback is registered (e.g. low-level direct-runtime
     * use), incoming SETUP_TRIGGER_REQUEST messages are acknowledged with
     * success — this keeps deploy-time triggers from failing against runtimes
     * that have no setup hooks.
     */
    public void registerSetupTriggerHandler(SetupTriggerHandler handler) {
        this.setupTriggerHandler = handler;
    }

    /**
     * Set the per-provider capabilities to declare on the next (or current)
     * connection. Idempotent; overwrites the previous value. Reconnects
     * re-send the latest declared value.
     */
    public void setProviderCapabilities(ProviderCapabilities capabilities) {
        this.providerCapabilities = capabilities;
    }

    /** The WS proxy adapter for service proxy requests in hosted mode. */
    public WSProxyAdapter getWSProxyAdapter() {
        return wsProxyAdapter;
    }

    /** The engine API implementation handlers are initialized with. */
    public EngineClient getEngineClient() {
        return engineClient;
    }

    public List<Handler> getAllHandlers() {
        var all = new ArrayList<Handler>();
        all.addAll(transactionHandlers.values());
        all.addAll(eventSources.values());
        all.addAll(eventProcessors.values());
        return all;
    }

    Duration resultTimeout() {
        return config.resultTimeout() != null ? config.resultTimeout() : Duration.ofMinutes(2);
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /**
     * Initialize all handlers and start the runtime. In outbound mode
     * (a {@code url} is configured), connects to the workflow engine and
     * blocks until the first successful connection (retrying with exponential
     * backoff, bounded by {@code maxAttempts} when set). In inbound mode
     * (a {@code server} is configured), starts a local WebSocket server and
     * blocks until it is listening; the engine dials in.
     */
    public void start() throws Exception {
        for (var handler : getAllHandlers()) {
            handler.init(engineClient);
            log.debug("Initialized handler {}", handler.name());
        }

        if (config.server() != null) {
            // Inbound: the engine/provider-proxy dials in, rather than this
            // process dialing out (see ServerConfig).
            log.info("Starting handler runtime in inbound mode provider={}", config.providerName());
            shouldReconnect.set(false);
            startInboundServer();
            return;
        }

        log.info("Starting handler runtime and registering with workflow engine at {} provider={}",
                config.url(), config.providerName());
        try {
            connectWithRetry(0).get();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    /**
     * Stop the runtime and close all handlers.
     */
    public void stop() {
        log.info("Stopping handler runtime");
        stopped.set(true);
        shouldReconnect.set(false);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        var server = this.wsServer;
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                log.debug("Error closing inbound WebSocket server: {}", e.getMessage());
            }
        }
        var ws = this.webSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").join();
            } catch (Exception e) {
                log.debug("Error closing WebSocket: {}", e.getMessage());
            }
        }
        for (var handler : getAllHandlers()) {
            try {
                handler.close();
            } catch (Exception e) {
                log.warn("Error closing handler {}: {}", handler.name(), e.getMessage());
            }
        }
        dispatchExecutor.shutdown();
        scheduler.shutdown();
        stoppedFuture.complete(null);
    }

    @Override
    public void close() {
        stop();
    }

    public void waitStopped() throws InterruptedException {
        try {
            stoppedFuture.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isWebSocketConnected() {
        return connected.get();
    }

    /**
     * Send a message over the WebSocket as JSON.
     */
    @Override
    public void sendMessage(Object message) {
        if (config.server() != null) {
            var conn = this.inboundConnection;
            if (conn == null || !connected.get()) {
                log.warn("Attempted to send message while disconnected");
                return;
            }
            try {
                conn.send(JSON.MAPPER.writeValueAsString(message));
            } catch (Exception e) {
                log.error("Failed to send WS message", e);
            }
            return;
        }
        var ws = this.webSocket;
        if (ws == null || !connected.get()) {
            log.warn("Attempted to send message while disconnected");
            return;
        }
        try {
            ws.sendText(JSON.MAPPER.writeValueAsString(message), true).join();
        } catch (Exception e) {
            log.error("Failed to send WS message", e);
        }
    }

    // ── Connection management ───────────────────────────────────────────────

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

        var result = new CompletableFuture<Void>();
        wsBuilder.buildAsync(config.url(), new Listener())
                .whenComplete((ws, error) -> {
                    if (error == null) {
                        this.webSocket = ws;
                        connected.set(true);
                        log.info("WebSocket connected to {}", config.url());
                        registerProviderAndHandlers();
                        startHeartbeat();
                        result.complete(null);
                        return;
                    }
                    log.warn("WebSocket connection failed (attempt {}): {}", attempt, error.getMessage());
                    var maxAttempts = config.maxAttempts();
                    if (maxAttempts > 0 && attempt + 1 >= maxAttempts) {
                        result.completeExceptionally(error);
                        return;
                    }
                    if (!shouldReconnect.get() || stopped.get()) {
                        result.completeExceptionally(error);
                        return;
                    }
                    var delay = computeBackoff(attempt);
                    log.info("Reconnecting in {}ms", delay);
                    scheduler.schedule(() ->
                            connectWithRetry(attempt + 1).whenComplete((v, e) -> {
                                if (e == null) {
                                    result.complete(null);
                                } else {
                                    result.completeExceptionally(e);
                                }
                            }), delay, TimeUnit.MILLISECONDS);
                });
        return result;
    }

    private void applyAuth(WebSocket.Builder wsBuilder) {
        if (config.auth() == null) {
            return;
        }
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

    // ── Inbound mode (engine dials in) ─────────────────────────────────────

    /**
     * Start a WebSocket server on {@code config.server()}'s address/port and
     * wait for the engine/provider-proxy to dial in. Blocks until the server
     * is actually listening, and throws if the bind fails. Unlike outbound
     * mode, a dropped connection is not "reconnected" from here — the server
     * keeps listening and the next {@code onOpen} picks up where
     * {@code onClose} left off.
     *
     * <p>Dead-connection detection uses the underlying library's
     * {@code connectionLostTimeout} ping/pong keepalive rather than this
     * class's manual heartbeat (which is written in terms of the outbound
     * {@code java.net.http.WebSocket} type) — same end result, different
     * mechanism.
     */
    private void startInboundServer() throws Exception {
        var serverConfig = config.server();
        var address = serverConfig.address() != null ? serverConfig.address() : "0.0.0.0";
        var port = serverConfig.resolvedPort();

        var startupLatch = new CountDownLatch(1);
        var startupError = new AtomicReference<Exception>();

        wsServer = new org.java_websocket.server.WebSocketServer(new InetSocketAddress(address, port)) {
            @Override
            public void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake) {
                log.info("Inbound WebSocket connection from {}", conn.getRemoteSocketAddress());
                inboundConnection = conn;
                connected.set(true);
                registerProviderAndHandlers();
            }

            @Override
            public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {
                log.info("Inbound WebSocket closed: {} {}", code, reason);
                if (inboundConnection == conn) {
                    inboundConnection = null;
                    connected.set(false);
                    engineClient.cancelAll();
                    wsProxyAdapter.cancelAll();
                }
            }

            @Override
            public void onMessage(org.java_websocket.WebSocket conn, String message) {
                handleMessage(message);
            }

            @Override
            public void onError(org.java_websocket.WebSocket conn, Exception ex) {
                log.error("Inbound WebSocket error: {}", ex.getMessage());
                if (conn == null) {
                    // Server-level error (e.g. bind failure) — surface it to
                    // start() if we're still waiting on the bind.
                    startupError.set(ex);
                    startupLatch.countDown();
                }
            }

            @Override
            public void onStart() {
                log.info("Inbound WebSocket server listening on {}:{}", address, port);
                startupLatch.countDown();
            }
        };

        var heartbeatSeconds = (int) (config.heartbeatInterval() != null
                ? config.heartbeatInterval().toSeconds() : Duration.ofSeconds(30).toSeconds());
        wsServer.setConnectionLostTimeout(Math.max(heartbeatSeconds, 1));
        wsServer.setReuseAddr(true);

        if (serverConfig.tls() != null && serverConfig.tls().enabled()) {
            wsServer.setWebSocketFactory(InboundTls.serverFactory(serverConfig.tls()));
        }

        wsServer.start();
        if (!startupLatch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timed out waiting for inbound WebSocket server to start on "
                    + address + ":" + port);
        }
        if (startupError.get() != null) {
            throw startupError.get();
        }
    }

    private long computeBackoff(int attempt) {
        var delay = config.reconnectDelay() != null ? config.reconnectDelay() : Duration.ofSeconds(1);
        var maxDelay = config.maxReconnectDelay() != null ? config.maxReconnectDelay() : Duration.ofSeconds(30);
        var factor = config.reconnectDelayFactor() > 0 ? config.reconnectDelayFactor() : 2.0;
        var ms = (long) (delay.toMillis() * Math.pow(factor, attempt));
        return Math.min(ms, maxDelay.toMillis());
    }

    private void onDisconnect() {
        connected.set(false);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }

        // Cancel in-flight engine API and service proxy requests
        engineClient.cancelAll();
        wsProxyAdapter.cancelAll();

        if (shouldReconnect.get() && !stopped.get()) {
            log.info("Connection lost, reconnecting...");
            connectWithRetry(0);
        } else {
            stoppedFuture.complete(null);
        }
    }

    // ── Handler registration ────────────────────────────────────────────────

    private void registerProviderAndHandlers() {
        log.info("Registering provider and handlers provider={} transactionHandlers={} eventSources={} eventProcessors={}",
                config.providerName(), transactionHandlers.size(), eventSources.size(), eventProcessors.size());

        // `capabilities` is omitted when never declared, keeping the message
        // byte-identical to the pre-capability wire format for direct-runtime users.
        sendMessage(WSRegisterProvider.of(
                generateId(), config.providerName(), config.providerMetadata(), providerCapabilities));

        for (var name : transactionHandlers.keySet()) {
            registerHandler(name, WSHandlerType.TRANSACTION_HANDLER);
        }
        for (var name : eventSources.keySet()) {
            registerHandler(name, WSHandlerType.EVENT_SOURCE);
        }
        for (var name : eventProcessors.keySet()) {
            registerHandler(name, WSHandlerType.EVENT_PROCESSOR);
        }
    }

    private void registerHandler(String handlerName, WSHandlerType handlerType) {
        log.debug("Registering handler name={} type={}", handlerName, handlerType);
        sendMessage(WSRegisterHandler.of(handlerName, handlerType));
    }

    // ── Message routing ─────────────────────────────────────────────────────

    void handleMessage(String json) {
        try {
            var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
            if (envelope.messageType() == null) {
                log.warn("Received message with no messageType");
                return;
            }
            switch (envelope.messageType()) {
                case HANDLE_TRANSACTIONS -> dispatch(json, WSHandleTransactions.class, this::handleTransactionsMessage);
                case EVENT_PROCESSOR_BATCH -> dispatch(json, WSEventProcessorBatchRequest.class, this::handleEventProcessorBatch);
                case EVENT_SOURCE_CONFIG -> dispatch(json, WSEventSourceConfig.class, this::handleEventSourceConfig);
                case EVENT_SOURCE_POLL -> dispatch(json, WSListenerPollRequest.class, this::handleEventSourcePoll);
                case EVENT_SOURCE_VALIDATE_CONFIG -> dispatch(json, WSEventSourceValidateConfigRequest.class, this::handleEventSourceValidateConfig);
                case EVENT_SOURCE_DELETE -> dispatch(json, WSEventSourceDeleteRequest.class, this::handleEventSourceDelete);
                case ENGINE_API_SUBMIT_TRANSACTIONS_RESULT -> engineClient.handleResponse(
                        JSON.MAPPER.readValue(json, io.kaleido.workflowengine.sdk.protocol.WSEngineAPISubmitTransactionsResult.class));
                case SERVICE_PROXY_RESPONSE -> wsProxyAdapter.handleResponse(
                        JSON.MAPPER.readValue(json, ServiceProxyResponse.class));
                case SETUP_TRIGGER_REQUEST -> dispatch(json, WSSetupTriggerRequest.class, this::handleSetupTriggerRequest);
                case PROTOCOL_ERROR -> log.error("Protocol error received: {}", envelope.error());
                case REGISTER_PROVIDER, REGISTER_HANDLER -> log.debug("Registration response: {}", envelope.messageType());
                default -> log.warn("Unknown message type: {}", envelope.messageType());
            }
        } catch (Exception e) {
            log.error("Error processing message", e);
        }
    }

    private <M> void dispatch(String json, Class<M> type, java.util.function.Consumer<M> handler) throws Exception {
        var message = JSON.MAPPER.readValue(json, type);
        dispatchExecutor.submit(() -> {
            try {
                handler.accept(message);
            } catch (Exception e) {
                log.error("Error dispatching message", e);
            }
        });
    }

    /**
     * Build a per-request context from a message envelope. When the envelope
     * carries a deadline, a timer cancels the context's signal once the
     * deadline passes.
     */
    public RequestContext newRequestContext(WSHandlerEnvelope envelope) {
        var signal = new CancellationSignal();
        ScheduledFuture<?> deadlineTask = null;
        if (envelope.deadline() != null && !envelope.deadline().isEmpty()) {
            try {
                var startTime = System.currentTimeMillis();
                var deadlineMs = Instant.parse(envelope.deadline()).toEpochMilli() - startTime;
                deadlineTask = scheduler.schedule(() ->
                        signal.cancel("deadline exceeded after " + (System.currentTimeMillis() - startTime) + "ms"),
                        deadlineMs, TimeUnit.MILLISECONDS);
            } catch (DateTimeParseException e) {
                log.warn("Ignoring unparseable deadline '{}'", envelope.deadline());
            }
        }
        var cancelTimer = deadlineTask;
        return new RequestContext(
                envelope.id(),
                envelope.authTokens(),
                envelope.authRef(),
                signal,
                cancelTimer != null ? () -> cancelTimer.cancel(false) : null);
    }

    private void handleTransactionsMessage(WSHandleTransactions batch) {
        if (batch.handler() == null) {
            log.error("Handler not set in transactions message");
            return;
        }
        log.debug("Handling transactions handler={} batchId={} count={}",
                batch.handler(), batch.id(), batch.transactions() != null ? batch.transactions().size() : 0);

        var reqContext = newRequestContext(batch);
        var response = WSHandleTransactionsResult.forRequest(batch);
        try {
            var handler = transactionHandlers.get(batch.handler());
            if (handler != null) {
                handler.transactionHandlerBatch(reqContext, response, batch);
            } else {
                response.setError("No transaction handler registered: " + batch.handler());
                log.error(response.getError());
            }
        } catch (Exception e) {
            log.error("Handler failed handler={}", batch.handler(), e);
            response.setResults(batch.transactions().stream()
                    .map(t -> WSEvaluateReplyResult.error(e.getMessage()))
                    .toList());
        } finally {
            reqContext.cancel();
        }
        sendMessage(response);
    }

    private void handleEventProcessorBatch(WSEventProcessorBatchRequest batch) {
        log.debug("Handling event processor batch handler={} batchId={} count={}",
                batch.handler(), batch.id(), batch.events() != null ? batch.events().size() : 0);

        var reqContext = newRequestContext(batch);
        var response = WSEventProcessorBatchResult.forRequest(batch);
        try {
            var eventProcessor = eventProcessors.get(batch.handler() != null ? batch.handler() : "");
            if (eventProcessor != null) {
                eventProcessor.eventProcessorBatch(reqContext, response, batch);
            } else {
                response.setError("No event processor registered: " + batch.handler());
                log.error(response.getError());
            }
        } catch (Exception e) {
            log.error("Event processor batch failed handler={}", batch.handler(), e);
            response.setError(e.getMessage());
        } finally {
            reqContext.cancel();
        }
        sendMessage(response);
    }

    private void handleEventSourceConfig(WSEventSourceConfig config) {
        log.debug("Event source config stream={} name={}", config.streamId(), config.streamName());
        eventSourceConfigs.put(config.streamId(), config);
    }

    private void handleEventSourcePoll(WSListenerPollRequest request) {
        var reqContext = newRequestContext(request);
        var response = WSListenerPollResult.forRequest(request);
        try {
            var eventSource = eventSources.get(request.handler() != null ? request.handler() : "");
            var config = eventSourceConfigs.get(request.streamId());
            if (eventSource != null && config != null) {
                eventSource.eventSourcePoll(reqContext, config, response, request);
            } else {
                response.setError("No event source or config: " + request.handler() + "/" + request.streamId());
                log.error(response.getError());
            }
        } catch (Exception e) {
            log.error("Event source poll failed handler={}", request.handler(), e);
            response.setError(e.getMessage());
        } finally {
            reqContext.cancel();
        }
        sendMessage(response);
    }

    private void handleEventSourceValidateConfig(WSEventSourceValidateConfigRequest request) {
        log.debug("Event source validate config handler={}", request.handler());

        var reqContext = newRequestContext(request);
        var response = WSEventSourceValidateConfigResult.forRequest(request);
        try {
            var eventSource = eventSources.get(request.handler() != null ? request.handler() : "");
            if (eventSource != null) {
                eventSource.eventSourceValidateConfig(reqContext, response, request);
            } else {
                response.setError("No event source registered: " + request.handler());
                log.error(response.getError());
            }
        } catch (Exception e) {
            log.error("Event source validate config failed handler={}", request.handler(), e);
            response.setError(e.getMessage());
        } finally {
            reqContext.cancel();
        }
        sendMessage(response);
    }

    private void handleEventSourceDelete(WSEventSourceDeleteRequest request) {
        log.debug("Event source delete handler={} stream={}", request.handler(), request.streamId());

        var reqContext = newRequestContext(request);
        var response = WSEventSourceDeleteResult.forRequest(request);
        try {
            var eventSource = eventSources.get(request.handler() != null ? request.handler() : "");
            if (eventSource != null) {
                eventSource.eventSourceDelete(reqContext, response, request);
                eventSourceConfigs.remove(request.streamId());
            } else {
                response.setError("No event source registered: " + request.handler());
                log.error(response.getError());
            }
        } catch (Exception e) {
            log.error("Event source delete failed handler={}", request.handler(), e);
            response.setError(e.getMessage());
        } finally {
            reqContext.cancel();
        }
        sendMessage(response);
    }

    private void handleSetupTriggerRequest(WSSetupTriggerRequest request) {
        var requestId = request.requestId();
        var authRef = request.authRef();
        log.info("Setup trigger request received requestId={} authRefLen={}",
                requestId, authRef != null ? authRef.length() : 0);
        if (requestId == null || requestId.isEmpty()) {
            log.warn("Setup trigger request missing requestId; dropping");
            return;
        }
        List<String> errors = List.of();
        var handler = setupTriggerHandler;
        if (handler != null) {
            try {
                errors = handler.runSetup(authRef != null ? authRef : "");
            } catch (Exception e) {
                errors = List.of(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }
        // When no handler is registered (low-level runtime use), we still acknowledge so
        // service-manager's best-effort dispatch sees a successful response rather than a
        // timeout — there are simply no setup hooks to run.
        sendMessage(errors == null || errors.isEmpty()
                ? WSSetupTriggerResponse.success(requestId)
                : WSSetupTriggerResponse.error(requestId, errors));
    }

    // ── Heartbeat ───────────────────────────────────────────────────────────

    private void startHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        lastPong.set(System.currentTimeMillis());
        var interval = config.heartbeatInterval() != null ? config.heartbeatInterval() : Duration.ofSeconds(30);
        var pongTimeout = config.pongTimeout() != null ? config.pongTimeout() : Duration.ofSeconds(10);
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            var ws = this.webSocket;
            if (ws == null) {
                return;
            }
            var elapsed = System.currentTimeMillis() - lastPong.get();
            if (elapsed > pongTimeout.toMillis() + interval.toMillis()) {
                log.warn("Pong timeout ({}ms), forcing reconnect", elapsed);
                ws.abort();
                onDisconnect();
                return;
            }
            ws.sendPing(ByteBuffer.allocate(0)).exceptionally(e -> {
                log.debug("Ping send failed: {}", e.getMessage());
                return null;
            });
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    private class Listener implements WebSocket.Listener {
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
