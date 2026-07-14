// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.client;

import io.kaleido.workflowengine.sdk.app.EventProcessorContext;
import io.kaleido.workflowengine.sdk.app.EventProcessorDef;
import io.kaleido.workflowengine.sdk.app.SetupContext;
import io.kaleido.workflowengine.sdk.app.SetupHook;
import io.kaleido.workflowengine.sdk.app.TransactionHandlerRegistration;
import io.kaleido.workflowengine.sdk.config.ClientConfig;
import io.kaleido.workflowengine.sdk.config.ConfigLoader;
import io.kaleido.workflowengine.sdk.config.SetupLifecycle;
import io.kaleido.workflowengine.sdk.errors.SDKErrors;
import io.kaleido.workflowengine.sdk.factories.EventProcessorFactory;
import io.kaleido.workflowengine.sdk.handlers.CancellationSignal;
import io.kaleido.workflowengine.sdk.handlers.EventProcessor;
import io.kaleido.workflowengine.sdk.handlers.EventSource;
import io.kaleido.workflowengine.sdk.handlers.Handler;
import io.kaleido.workflowengine.sdk.handlers.TransactionHandler;
import io.kaleido.workflowengine.sdk.protocol.ProviderCapabilities;
import io.kaleido.workflowengine.sdk.runtime.HandlerRuntime;
import io.kaleido.workflowengine.sdk.service.ServiceBindingConfig;
import io.kaleido.workflowengine.sdk.service.ServiceClientOptions;
import io.kaleido.workflowengine.sdk.service.WSProxyAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The primary client for hosting workflow engine handlers.
 *
 * <p>Register handlers with the fluent {@link #eventProcessor},
 * {@link #transactionHandler} and {@link #eventSource} methods (or the
 * low-level {@code registerX} methods), then call {@link #start()} to connect.
 * Handler {@code setup} hooks run according to the configured
 * {@link SetupLifecycle}.
 */
public class WorkflowEngineClient {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineClient.class);

    private final HandlerRuntime runtime;
    private final ClientConfig config;
    private final List<RegisteredHandler> registeredHandlers = new ArrayList<>();

    private sealed interface RegisteredHandler {
        String name();
        SetupHook setup();

        record ForEventProcessor(String name, EventProcessorDef def) implements RegisteredHandler {
            @Override public SetupHook setup() { return def.setup(); }
        }

        record ForTransactionHandler(String name, TransactionHandlerRegistration registration) implements RegisteredHandler {
            @Override public SetupHook setup() { return registration.setup(); }
        }

        record ForEventSource(String name, EventSource source) implements RegisteredHandler {
            @Override public SetupHook setup() { return null; }
        }
    }

    public WorkflowEngineClient(ClientConfig config) {
        this(config, new HandlerRuntime(config));
    }

    WorkflowEngineClient(ClientConfig config, HandlerRuntime runtime) {
        this.config = config;
        this.runtime = runtime;
    }

    /**
     * Load config from the YAML file named by {@code KALEIDO_CONFIG_FILE} (or
     * the legacy {@code WFE_CONFIG_FILE}) and return a configured client.
     */
    public static WorkflowEngineClient fromConfigFile() {
        return fromConfigFile(null);
    }

    /**
     * Load config from a YAML file and return a configured client. When
     * {@code path} is null, the {@code KALEIDO_CONFIG_FILE} /
     * {@code WFE_CONFIG_FILE} env vars name the file.
     */
    public static WorkflowEngineClient fromConfigFile(Path path) {
        var configPath = ConfigLoader.resolveConfigPath(path != null ? path.toString() : null);
        if (configPath == null) {
            throw SDKErrors.newError(SDKErrors.MSG_CONFIG_FILE_NOT_SET, ConfigLoader.KALEIDO_CONFIG_FILE);
        }
        var kaleidoConfigPath = Path.of(configPath);
        var clientConfig = ConfigLoader.load(kaleidoConfigPath);
        var customConfig = ConfigLoader.loadCustomConfig(kaleidoConfigPath);
        if (customConfig != null) {
            clientConfig = ClientConfig.builder()
                    .url(clientConfig.url())
                    .providerName(clientConfig.providerName())
                    .providerMetadata(clientConfig.providerMetadata())
                    .auth(clientConfig.auth())
                    .extraHeaders(clientConfig.extraHeaders())
                    .reconnectDelay(clientConfig.reconnectDelay())
                    .maxReconnectDelay(clientConfig.maxReconnectDelay())
                    .reconnectDelayFactor(clientConfig.reconnectDelayFactor())
                    .maxAttempts(clientConfig.maxAttempts())
                    .heartbeatInterval(clientConfig.heartbeatInterval())
                    .pongTimeout(clientConfig.pongTimeout())
                    .resultTimeout(clientConfig.resultTimeout())
                    .setupLifecycle(clientConfig.setupLifecycle())
                    .serviceBindings(clientConfig.serviceBindings())
                    .customConfig(customConfig)
                    .build();
        }
        return new WorkflowEngineClient(clientConfig);
    }

    // ── Builder API ─────────────────────────────────────────────────────────

    /**
     * Register an event processor handler. The handler name must be unique
     * within this client.
     */
    public WorkflowEngineClient eventProcessor(String name, EventProcessorDef def) {
        assertUniqueHandlerName(name);
        registeredHandlers.add(new RegisteredHandler.ForEventProcessor(name, def));
        return this;
    }

    /**
     * Register a transaction handler with an optional setup hook. The handler
     * name must be unique within this client.
     */
    public WorkflowEngineClient transactionHandler(String name, TransactionHandlerRegistration registration) {
        assertUniqueHandlerName(name);
        registeredHandlers.add(new RegisteredHandler.ForTransactionHandler(name, registration));
        return this;
    }

    /**
     * Register a transaction handler with no setup hook.
     */
    public WorkflowEngineClient transactionHandler(String name, TransactionHandler handler) {
        return transactionHandler(name, TransactionHandlerRegistration.of(handler));
    }

    /**
     * Register an event source. The handler name is taken from
     * {@code source.name()} and must be unique within this client.
     */
    public WorkflowEngineClient eventSource(EventSource source) {
        var name = source.name();
        assertUniqueHandlerName(name);
        registeredHandlers.add(new RegisteredHandler.ForEventSource(name, source));
        return this;
    }

    /**
     * Run all handler {@code setup} hooks, then return without connecting.
     * Use this as an init-container / migration step.
     */
    public void setup() throws Exception {
        var signal = new CancellationSignal();
        try {
            runSetupHooks(signal, null);
        } finally {
            signal.cancel(null);
        }
    }

    /**
     * Connect to the workflow engine and register handlers. With
     * {@link SetupLifecycle#BOOT} (default), setup() hooks also run here,
     * after the connect — so that hosted ws-proxy bindings have an established
     * WebSocket before setup() tries to call platform services. With
     * {@link SetupLifecycle#DEFERRED}, hooks do NOT run here; they run when
     * the proxy dispatches a SETUP_TRIGGER_REQUEST (issued by service-manager
     * as part of the deploy operation, carrying the deployer's authRef so
     * hosted-binding calls inside setup() authenticate as the deploying user).
     */
    public void start() throws Exception {
        registerBuilderHandlers();
        // Declare per-provider capabilities on the WS. Computed here (after
        // registerBuilderHandlers ran, before connect opens the socket) so the
        // very first REGISTER_PROVIDER message carries the flags. On reconnect
        // the runtime re-sends the same declared value.
        runtime.setProviderCapabilities(new ProviderCapabilities(hasAnySetupHook()));
        connect();
        if (config.setupLifecycle() == SetupLifecycle.DEFERRED) {
            // Trigger handler is registered ONLY in deferred mode. In boot mode, any stray
            // SETUP_TRIGGER_REQUEST (e.g. a misconfigured platform sending one despite the
            // operator not emitting `deferred`) falls through to the runtime's default
            // ack-with-success-no-op path — service-manager isn't blocked, and setup hooks
            // are not run twice.
            runtime.registerSetupTriggerHandler(this::runSetupOnTrigger);
            log.info("setupLifecycle=deferred: skipping boot-time setup hooks (waiting for SETUP_TRIGGER_REQUEST)");
            return;
        }
        var signal = new CancellationSignal();
        runSetupHooks(signal, null);
    }

    /**
     * Runs all setup hooks with the trigger's authRef in scope, collecting an
     * error per failed hook rather than stopping at the first failure.
     */
    List<String> runSetupOnTrigger(String authRef) {
        var signal = new CancellationSignal();
        var errors = new ArrayList<String>();
        try {
            for (var registered : registeredHandlers) {
                var setup = registered.setup();
                if (setup == null) {
                    continue;
                }
                log.info("Running setup hook for handler '{}' (triggered, authRef={})",
                        registered.name(),
                        authRef != null && !authRef.isEmpty()
                                ? authRef.substring(0, Math.min(8, authRef.length())) + "..."
                                : "(none)");
                try {
                    setup.setup(buildSetupContext(registered.name(), signal, authRef));
                } catch (Exception e) {
                    var message = e.getMessage() != null ? e.getMessage() : e.toString();
                    errors.add(registered.name() + ": " + message);
                    log.error("Setup hook '{}' failed", registered.name(), e);
                }
            }
        } finally {
            signal.cancel(null);
        }
        return errors;
    }

    /**
     * Disconnect from the workflow engine.
     */
    public void stop() {
        disconnect();
    }

    public void close() {
        disconnect();
    }

    // ── Low-level registration API ──────────────────────────────────────────

    public void registerTransactionHandler(String name, TransactionHandler handler) {
        runtime.registerTransactionHandler(name, handler);
    }

    public void registerEventSource(String name, EventSource handler) {
        runtime.registerEventSource(name, handler);
    }

    public void registerEventProcessor(String name, EventProcessor handler) {
        runtime.registerEventProcessor(name, handler);
    }

    /**
     * Register any handler by its runtime type.
     */
    public void registerHandler(Handler handler) {
        switch (handler) {
            case TransactionHandler transactionHandler ->
                    registerTransactionHandler(handler.name(), transactionHandler);
            case EventSource eventSource -> registerEventSource(handler.name(), eventSource);
            case EventProcessor eventProcessor -> registerEventProcessor(handler.name(), eventProcessor);
            default -> throw SDKErrors.newError(SDKErrors.MSG_HANDLER_INVALID_TYPE, handler.name());
        }
    }

    public void connect() throws Exception {
        runtime.start();
    }

    public void disconnect() {
        runtime.stop();
    }

    public boolean isConnected() {
        return runtime.isWebSocketConnected();
    }

    public void waitStopped() throws InterruptedException {
        runtime.waitStopped();
    }

    public WSProxyAdapter getWSProxyAdapter() {
        return runtime.getWSProxyAdapter();
    }

    // ── Service bindings ────────────────────────────────────────────────────

    public Map<String, ServiceBindingConfig> getServiceBindings() {
        return new LinkedHashMap<>(config.serviceBindings());
    }

    public ServiceBindingConfig getServiceBinding(String name) {
        var binding = config.serviceBindings().get(name);
        if (binding == null) {
            var available = String.join(", ", config.serviceBindings().keySet());
            throw SDKErrors.newError(SDKErrors.MSG_SERVICE_BINDING_NOT_FOUND,
                    name, available.isEmpty() ? "(none)" : available);
        }
        return binding;
    }

    public ServiceClientOptions getServiceClientOptions(String name) {
        return getServiceClientOptions(name, null);
    }

    public ServiceClientOptions getServiceClientOptions(String name, String authRef) {
        var binding = getServiceBinding(name);
        return switch (binding) {
            case ServiceBindingConfig.Hosted hosted -> new ServiceClientOptions.WsProxy(
                    getWSProxyAdapter(), hosted.type(), hosted.id(), authRef);
            case ServiceBindingConfig.NonHosted nonHosted -> new ServiceClientOptions.Http(
                    nonHosted.url(), nonHosted.auth(), nonHosted.maxRetries(), nonHosted.timeout());
        };
    }

    // ── Private builder helpers ─────────────────────────────────────────────

    private SetupContext buildSetupContext(String handlerName, CancellationSignal signal, String authRef) {
        return new SetupContext(
                config.customConfig(),
                config.providerName(),
                handlerName,
                signal,
                name -> getServiceClientOptions(name, authRef));
    }

    private void runSetupHooks(CancellationSignal signal, String authRef) throws Exception {
        for (var registered : registeredHandlers) {
            var setup = registered.setup();
            if (setup != null) {
                log.info("Running setup hook for handler '{}'", registered.name());
                setup.setup(buildSetupContext(registered.name(), signal, authRef));
            }
        }
    }

    private void registerBuilderHandlers() {
        for (var registered : registeredHandlers) {
            switch (registered) {
                case RegisteredHandler.ForEventProcessor(String name, EventProcessorDef def) ->
                        registerEventProcessor(name, EventProcessorFactory.createEventProcessorBase(
                                name,
                                (reqContext, events, authRef) -> {
                                    // Use the per-request signal so the batch observes the request
                                    // deadline / cancellation — not a start-level signal that is never cancelled.
                                    var setupContext = buildSetupContext(name, reqContext.signal(), reqContext.authRef());
                                    var context = new EventProcessorContext(setupContext, reqContext.requestId());
                                    def.processBatch().processBatch(context, events);
                                }));
                case RegisteredHandler.ForTransactionHandler(String name, TransactionHandlerRegistration registration) ->
                        registerTransactionHandler(name, registration.handler());
                case RegisteredHandler.ForEventSource(String name, EventSource source) ->
                        registerEventSource(name, source);
            }
        }
    }

    private void assertUniqueHandlerName(String name) {
        if (registeredHandlers.stream().anyMatch(h -> h.name().equals(name))) {
            throw SDKErrors.newError(SDKErrors.MSG_HANDLER_ALREADY_REGISTERED, name);
        }
    }

    /**
     * True iff at least one registered handler defines a setup hook. Called at
     * start() time to populate {@link ProviderCapabilities}.
     */
    private boolean hasAnySetupHook() {
        return registeredHandlers.stream().anyMatch(h -> h.setup() != null);
    }
}
