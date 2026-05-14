// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Spring Boot configuration properties mapping the {@code kaleido.workflow-engine} YAML block.
 *
 * <p>These properties mirror the YAML shape consumed by
 * {@link io.kaleido.wfe.sdk.config.RuntimeConfig} so that Spring's type-safe binding,
 * environment-variable overrides, and relaxed binding all work out of the box.
 *
 * <p>The {@code WFE_CONFIG_FILE} environment variable continues to work for parity
 * with the TypeScript and Go SDKs: if set, the file it points to takes precedence
 * over Spring property binding.
 *
 * <p>Outbound mode (SDK dials engine) is selected by setting {@link #url}; server
 * mode (engine dials SDK) is selected by setting {@link #server}. Setting both is
 * an error.
 */
@ConfigurationProperties(prefix = "kaleido.workflow-engine")
public class WorkflowEngineProperties {

    private String providerName;
    private Map<String, Object> providerMetadata;
    private String url;
    private AuthProperties auth;
    private ServerProperties server;
    private Duration retryDelay = Duration.ofSeconds(1);
    private int maxRetries;
    private Duration heartbeatInterval = Duration.ofSeconds(30);
    private Duration pongTimeout = Duration.ofSeconds(10);
    private Duration resultTimeout = Duration.ofMinutes(2);

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public Map<String, Object> getProviderMetadata() { return providerMetadata; }
    public void setProviderMetadata(Map<String, Object> providerMetadata) { this.providerMetadata = providerMetadata; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public AuthProperties getAuth() { return auth; }
    public void setAuth(AuthProperties auth) { this.auth = auth; }
    public ServerProperties getServer() { return server; }
    public void setServer(ServerProperties server) { this.server = server; }
    public Duration getRetryDelay() { return retryDelay; }
    public void setRetryDelay(Duration retryDelay) { this.retryDelay = retryDelay; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public Duration getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
    public Duration getPongTimeout() { return pongTimeout; }
    public void setPongTimeout(Duration pongTimeout) { this.pongTimeout = pongTimeout; }
    public Duration getResultTimeout() { return resultTimeout; }
    public void setResultTimeout(Duration resultTimeout) { this.resultTimeout = resultTimeout; }

    public static class AuthProperties {
        private String type = "token";
        private String token;
        private String header;
        private String scheme;
        private String username;
        private String password;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getHeader() { return header; }
        public void setHeader(String header) { this.header = header; }
        public String getScheme() { return scheme; }
        public void setScheme(String scheme) { this.scheme = scheme; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /**
     * Mirrors {@link io.kaleido.wfe.sdk.config.ServerConfig}. The WS upgrade path
     * is hard-coded to {@code /ws} in the Go SDK; do not expose it here.
     */
    public static class ServerProperties {
        private String address = "0.0.0.0";
        private int port;
        private Duration heartbeatInterval = Duration.ofSeconds(15);
        private int requestsPerSecond;
        private int burst;
        private int readBufferSize;
        private int writeBufferSize;
        private TlsProperties tls;

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public Duration getHeartbeatInterval() { return heartbeatInterval; }
        public void setHeartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
        public int getRequestsPerSecond() { return requestsPerSecond; }
        public void setRequestsPerSecond(int requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }
        public int getBurst() { return burst; }
        public void setBurst(int burst) { this.burst = burst; }
        public int getReadBufferSize() { return readBufferSize; }
        public void setReadBufferSize(int readBufferSize) { this.readBufferSize = readBufferSize; }
        public int getWriteBufferSize() { return writeBufferSize; }
        public void setWriteBufferSize(int writeBufferSize) { this.writeBufferSize = writeBufferSize; }
        public TlsProperties getTls() { return tls; }
        public void setTls(TlsProperties tls) { this.tls = tls; }
    }

    /**
     * Mirrors {@link io.kaleido.wfe.sdk.config.ServerConfig.TlsConfig}. mTLS is the
     * only built-in identity check for inbound engine connections -- there is no
     * bearer / header auth at the upgrade.
     */
    public static class TlsProperties {
        private boolean enabled;
        private String certFile;
        private String keyFile;
        private String caFile;
        private boolean clientAuth;
        private Map<String, String> requiredDnAttributes;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCertFile() { return certFile; }
        public void setCertFile(String certFile) { this.certFile = certFile; }
        public String getKeyFile() { return keyFile; }
        public void setKeyFile(String keyFile) { this.keyFile = keyFile; }
        public String getCaFile() { return caFile; }
        public void setCaFile(String caFile) { this.caFile = caFile; }
        public boolean isClientAuth() { return clientAuth; }
        public void setClientAuth(boolean clientAuth) { this.clientAuth = clientAuth; }
        public Map<String, String> getRequiredDnAttributes() { return requiredDnAttributes; }
        public void setRequiredDnAttributes(Map<String, String> requiredDnAttributes) {
            this.requiredDnAttributes = requiredDnAttributes;
        }
    }
}
