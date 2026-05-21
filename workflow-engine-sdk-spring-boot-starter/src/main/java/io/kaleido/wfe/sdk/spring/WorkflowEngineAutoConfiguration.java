// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.spring;

import io.kaleido.wfe.sdk.client.WFEWebSocketClient;
import io.kaleido.wfe.sdk.config.AuthConfig;
import io.kaleido.wfe.sdk.config.RuntimeConfig;
import io.kaleido.wfe.sdk.config.ServerConfig;
import io.kaleido.wfe.sdk.handlers.*;
import io.kaleido.wfe.sdk.protocol.JSON;
import io.kaleido.wfe.sdk.server.WFEWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Auto-configuration for the Kaleido Workflow Engine SDK.
 *
 * <p>Discovers all {@link TransactionHandler}, {@link EventProcessor}, and
 * {@link EventSource} beans in the application context (including those
 * annotated with {@link KaleidoTransactionHandler} and {@link KaleidoEventProcessor}),
 * wires them into a {@link HandlerSet}, and manages a {@link WFEWebSocketClient}
 * (outbound mode) or {@link WFEWebSocketServer} (server mode) via Spring's
 * {@link SmartLifecycle}.
 */
@AutoConfiguration
@EnableConfigurationProperties(WorkflowEngineProperties.class)
public class WorkflowEngineAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RuntimeConfig kaleidoRuntimeConfig(WorkflowEngineProperties props) {
        var envPath = System.getenv(RuntimeConfig.ENV_CONFIG_FILE);
        if (envPath != null && !envPath.isBlank()) {
            log.info("Loading WFE config from {}", envPath);
            return RuntimeConfig.fromYaml(Path.of(envPath));
        }

        var builder = RuntimeConfig.builder()
                .providerName(props.getProviderName())
                .reconnectDelay(props.getRetryDelay())
                .maxAttempts(props.getMaxRetries())
                .heartbeatInterval(props.getHeartbeatInterval())
                .pongTimeout(props.getPongTimeout())
                .resultTimeout(props.getResultTimeout());

        if (props.getProviderMetadata() != null) {
            builder.providerMetadata(JSON.MAPPER.valueToTree(props.getProviderMetadata()));
        }

        if (props.getUrl() != null && !props.getUrl().isBlank()) {
            builder.url(URI.create(RuntimeConfig.httpUrlToWsUrl(props.getUrl())));
        }

        if (props.getAuth() != null) {
            builder.auth(toAuthConfig(props.getAuth()));
        }

        if (props.getServer() != null && props.getServer().getPort() > 0) {
            builder.server(toServerConfig(props.getServer()));
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public HandlerSet kaleidoHandlerSet(
            ObjectProvider<TransactionHandler> txnHandlers,
            ObjectProvider<EventProcessor> eventProcessors,
            ObjectProvider<EventSource> eventSources) {

        return engineAPI -> {
            List<Handler> handlers = new ArrayList<>();

            txnHandlers.orderedStream().forEach(h -> {
                var adapted = adaptTransactionHandler(h);
                adapted.init(engineAPI);
                handlers.add(adapted);
            });

            eventProcessors.orderedStream().forEach(h -> {
                var adapted = adaptEventProcessor(h);
                adapted.init(engineAPI);
                handlers.add(adapted);
            });

            eventSources.orderedStream().forEach(h -> {
                var adapted = adaptEventSource(h);
                adapted.init(engineAPI);
                handlers.add(adapted);
            });

            log.info("Registered {} WFE handlers", handlers.size());
            return handlers;
        };
    }

    @Bean
    @ConditionalOnMissingBean(WFEWebSocketClient.class)
    @ConditionalOnProperty(prefix = "kaleido.workflow-engine", name = "url")
    public WFEWebSocketClient kaleidoWebSocketClient(RuntimeConfig config, HandlerSet handlerSet) {
        return new WFEWebSocketClient(config, handlerSet);
    }

    @Bean
    @ConditionalOnBean(WFEWebSocketClient.class)
    public SmartLifecycle kaleidoClientLifecycle(WFEWebSocketClient client, RuntimeConfig config) {
        return new SmartLifecycle() {
            private final AtomicBoolean running = new AtomicBoolean(false);

            @Override
            public void start() {
                if (running.compareAndSet(false, true)) {
                    if (config.url() == null) {
                        log.debug("No WFE URL configured, skipping client start");
                        running.set(false);
                        return;
                    }
                    log.info("Starting Kaleido WFE client");
                    client.connect();
                }
            }

            @Override
            public void stop() {
                if (running.compareAndSet(true, false)) {
                    log.info("Stopping Kaleido WFE client");
                    client.stop();
                }
            }

            @Override
            public boolean isRunning() { return running.get(); }

            @Override
            public int getPhase() { return Integer.MAX_VALUE - 100; }
        };
    }

    @Bean
    @ConditionalOnMissingBean(WFEWebSocketServer.class)
    @ConditionalOnProperty(prefix = "kaleido.workflow-engine.server", name = "port")
    public WFEWebSocketServer kaleidoWebSocketServer(
            RuntimeConfig config, HandlerSet handlerSet, WorkflowEngineProperties props) {
        return new WFEWebSocketServer(config, toServerConfig(props.getServer()), handlerSet);
    }

    @Bean
    @ConditionalOnBean(WFEWebSocketServer.class)
    public SmartLifecycle kaleidoServerLifecycle(WFEWebSocketServer server) {
        return new SmartLifecycle() {
            private final AtomicBoolean running = new AtomicBoolean(false);

            @Override
            public void start() {
                if (running.compareAndSet(false, true)) {
                    log.info("Starting Kaleido WFE server");
                    try {
                        server.start();
                    } catch (Exception e) {
                        running.set(false);
                        throw new RuntimeException("Failed to start WFE server", e);
                    }
                }
            }

            @Override
            public void stop() {
                if (running.compareAndSet(true, false)) {
                    log.info("Stopping Kaleido WFE server");
                    server.stop();
                }
            }

            @Override
            public boolean isRunning() { return running.get(); }

            @Override
            public int getPhase() { return Integer.MAX_VALUE - 100; }
        };
    }

    private static AuthConfig toAuthConfig(WorkflowEngineProperties.AuthProperties auth) {
        return switch (auth.getType()) {
            case "basic" -> new AuthConfig.BasicAuth(auth.getUsername(), auth.getPassword());
            default -> new AuthConfig.TokenAuth(auth.getToken(), auth.getHeader(), auth.getScheme());
        };
    }

    private static ServerConfig toServerConfig(WorkflowEngineProperties.ServerProperties sp) {
        ServerConfig.TlsConfig tls = null;
        if (sp.getTls() != null) {
            var t = sp.getTls();
            tls = new ServerConfig.TlsConfig(
                    t.isEnabled(), t.getCertFile(), t.getKeyFile(), t.getCaFile(),
                    t.isClientAuth(), t.getRequiredDnAttributes());
        }
        return new ServerConfig(
                sp.getAddress(),
                sp.getPort(),
                sp.getHeartbeatInterval(),
                sp.getRequestsPerSecond(),
                sp.getBurst(),
                sp.getReadBufferSize(),
                sp.getWriteBufferSize(),
                tls);
    }

    private static TransactionHandler adaptTransactionHandler(TransactionHandler handler) {
        var annotation = handler.getClass().getAnnotation(KaleidoTransactionHandler.class);
        if (annotation != null) {
            return new AnnotatedTransactionHandlerAdapter(annotation.value(), handler);
        }
        return handler;
    }

    private static EventProcessor adaptEventProcessor(EventProcessor handler) {
        var annotation = handler.getClass().getAnnotation(KaleidoEventProcessor.class);
        if (annotation != null) {
            return new AnnotatedEventProcessorAdapter(annotation.value(), handler);
        }
        return handler;
    }

    private static EventSource adaptEventSource(EventSource handler) {
        var annotation = handler.getClass().getAnnotation(KaleidoEventSource.class);
        if (annotation != null) {
            return new AnnotatedEventSourceAdapter(annotation.value(), handler);
        }
        return handler;
    }
}
