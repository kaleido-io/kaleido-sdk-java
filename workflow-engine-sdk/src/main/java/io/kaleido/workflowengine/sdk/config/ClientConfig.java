// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.service.ServiceBindingConfig;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Configuration for a workflow engine client. Construct with {@link #builder()}
 * or load from a YAML config file with {@link ConfigLoader}.
 */
public record ClientConfig(
        URI wsUrl,
        URI restUrl,
        ServerConfig server,
        String providerName,
        JsonNode providerMetadata,
        AuthConfig auth,
        TlsConfig tls,
        Map<String, String> extraHeaders,
        Duration reconnectDelay,
        Duration maxReconnectDelay,
        double reconnectDelayFactor,
        int maxAttempts,
        Duration heartbeatInterval,
        Duration pongTimeout,
        Duration resultTimeout,
        SetupLifecycle setupLifecycle,
        Map<String, ServiceBindingConfig> serviceBindings,
        JsonNode customConfig
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private URI wsUrl;
        private URI restUrl;
        private ServerConfig server;
        private String providerName;
        private JsonNode providerMetadata;
        private AuthConfig auth;
        private TlsConfig tls;
        private Map<String, String> extraHeaders;
        private Duration reconnectDelay = Duration.ofSeconds(1);
        private Duration maxReconnectDelay = Duration.ofSeconds(30);
        private double reconnectDelayFactor = 2.0;
        private int maxAttempts = 0;
        private Duration heartbeatInterval = Duration.ofSeconds(30);
        private Duration pongTimeout = Duration.ofSeconds(10);
        private Duration resultTimeout = Duration.ofMinutes(2);
        private SetupLifecycle setupLifecycle = SetupLifecycle.BOOT;
        private Map<String, ServiceBindingConfig> serviceBindings = Map.of();
        private JsonNode customConfig;

        public Builder wsUrl(URI wsUrl) { this.wsUrl = wsUrl; return this; }
        public Builder restUrl(URI restUrl) { this.restUrl = restUrl; return this; }
        public Builder server(ServerConfig server) { this.server = server; return this; }
        public Builder providerName(String providerName) { this.providerName = providerName; return this; }
        public Builder providerMetadata(JsonNode providerMetadata) { this.providerMetadata = providerMetadata; return this; }
        public Builder auth(AuthConfig auth) { this.auth = auth; return this; }
        public Builder tls(TlsConfig tls) { this.tls = tls; return this; }
        public Builder extraHeaders(Map<String, String> extraHeaders) { this.extraHeaders = extraHeaders; return this; }
        public Builder reconnectDelay(Duration reconnectDelay) { this.reconnectDelay = reconnectDelay; return this; }
        public Builder maxReconnectDelay(Duration maxReconnectDelay) { this.maxReconnectDelay = maxReconnectDelay; return this; }
        public Builder reconnectDelayFactor(double reconnectDelayFactor) { this.reconnectDelayFactor = reconnectDelayFactor; return this; }
        public Builder maxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; return this; }
        public Builder heartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; return this; }
        public Builder pongTimeout(Duration pongTimeout) { this.pongTimeout = pongTimeout; return this; }
        public Builder resultTimeout(Duration resultTimeout) { this.resultTimeout = resultTimeout; return this; }
        public Builder setupLifecycle(SetupLifecycle setupLifecycle) { this.setupLifecycle = setupLifecycle; return this; }
        public Builder serviceBindings(Map<String, ServiceBindingConfig> serviceBindings) { this.serviceBindings = serviceBindings; return this; }
        public Builder customConfig(JsonNode customConfig) { this.customConfig = customConfig; return this; }

        public ClientConfig build() {
            return new ClientConfig(wsUrl, restUrl, server, providerName, providerMetadata, auth, tls, extraHeaders,
                    reconnectDelay, maxReconnectDelay, reconnectDelayFactor, maxAttempts,
                    heartbeatInterval, pongTimeout, resultTimeout,
                    setupLifecycle, serviceBindings, customConfig);
        }
    }
}
