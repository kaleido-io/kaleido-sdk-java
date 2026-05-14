// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaleido.wfe.sdk.errors.SDKErrors;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime configuration for the workflow-engine SDK.
 *
 * <p>Describes both outbound (SDK dials engine) and server mode (engine dials SDK).
 * In outbound mode {@link #url()} and {@link #auth()} drive a {@code WFEWebSocketClient};
 * in server mode {@link #server()} drives a {@code WFEWebSocketServer}. The two
 * blocks are mutually exclusive -- setting both in YAML fails with
 * {@link SDKErrors#MUTUALLY_EXCLUSIVE_CONFIG}.
 *
 * <p>See {@code .cursor/plans/go-sdk.md} for the protocol source of truth.
 */
public record RuntimeConfig(
        URI url,
        ServerConfig server,
        String providerName,
        JsonNode providerMetadata,
        AuthConfig auth,
        Map<String, String> extraHeaders,
        Duration reconnectDelay,
        Duration maxReconnectDelay,
        double reconnectDelayFactor,
        int maxAttempts,
        Duration heartbeatInterval,
        Duration pongTimeout,
        Duration resultTimeout
) {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d+)(ms|s|m|h)$");
    public static final String ENV_CONFIG_FILE = "WFE_CONFIG_FILE";

    public static Builder builder() {
        return new Builder();
    }

    public static RuntimeConfig fromYaml(Path file) {
        try {
            var content = Files.readString(file);
            var root = YAML_MAPPER.readTree(content);
            var wfe = root.path("workflow-engine");
            if (wfe.isMissingNode()) {
                throw SDKErrors.error(SDKErrors.CONFIG_FILE_READ_FAILED,
                        "YAML must have a 'workflow-engine' root key");
            }
            return parseYamlNode(wfe);
        } catch (IOException e) {
            throw SDKErrors.error(SDKErrors.CONFIG_FILE_READ_FAILED,
                    "Failed to read config: " + file, e);
        }
    }

    public static RuntimeConfig fromYaml(java.io.InputStream inputStream) {
        try {
            var root = YAML_MAPPER.readTree(inputStream);
            var wfe = root.path("workflow-engine");
            if (wfe.isMissingNode()) {
                throw SDKErrors.error(SDKErrors.CONFIG_FILE_READ_FAILED,
                        "YAML must have a 'workflow-engine' root key");
            }
            return parseYamlNode(wfe);
        } catch (IOException e) {
            throw SDKErrors.error(SDKErrors.CONFIG_FILE_READ_FAILED,
                    "Failed to read config from stream", e);
        }
    }

    public static RuntimeConfig fromEnv() {
        var configFile = System.getenv(ENV_CONFIG_FILE);
        if (configFile == null || configFile.isEmpty()) {
            throw SDKErrors.error(SDKErrors.CONFIG_FILE_NOT_SET,
                    ENV_CONFIG_FILE + " environment variable is not set");
        }
        return fromYaml(Path.of(configFile));
    }

    private static RuntimeConfig parseYamlNode(JsonNode wfe) {
        var b = builder();

        var providerName = wfe.has("providerName") ? wfe.get("providerName").asText() : null;
        if (providerName == null || providerName.isEmpty()) {
            throw SDKErrors.error(SDKErrors.CONFIG_FILE_READ_FAILED, "Provider name not set");
        }
        b.providerName(providerName);

        if (wfe.has("providerMetadata")) {
            b.providerMetadata(wfe.get("providerMetadata"));
        }
        if (wfe.has("maxRetries")) {
            b.maxAttempts(wfe.get("maxRetries").asInt());
        }
        if (wfe.has("retryDelay")) {
            var retryNode = wfe.get("retryDelay");
            var retryStr = retryNode.isNumber()
                    ? String.valueOf(retryNode.asInt())
                    : retryNode.asText();
            b.reconnectDelay(parseTimeString(retryStr));
        }

        var urlStr = wfe.has("url") ? wfe.get("url").asText("") : "";
        var hasUrl = !urlStr.isEmpty();
        var hasAuth = wfe.has("auth") && wfe.get("auth").isObject();
        var hasServer = wfe.has("server") && wfe.get("server").isObject();

        if (hasUrl && hasServer) {
            throw SDKErrors.error(SDKErrors.MUTUALLY_EXCLUSIVE_CONFIG,
                    "Cannot set both 'url' and 'server' in workflow-engine config; pick one mode");
        }

        if (hasServer) {
            b.server(parseServer(wfe.get("server")));
        } else if (hasUrl) {
            b.url(URI.create(httpUrlToWsUrl(urlStr)));
            if (hasAuth) {
                b.auth(parseAuth(wfe.get("auth")));
            }
        } else {
            throw SDKErrors.error(SDKErrors.CONFIG_FILE_READ_FAILED,
                    "Missing 'url' or 'server' in workflow-engine config");
        }

        return b.build();
    }

    private static ServerConfig parseServer(JsonNode server) {
        var address = server.has("address") ? server.get("address").asText("0.0.0.0") : "0.0.0.0";
        var port = server.has("port") ? server.get("port").asInt(0) : 0;
        if (port <= 0) {
            throw SDKErrors.error(SDKErrors.CONFIG_FILE_READ_FAILED,
                    "server.port must be a positive integer");
        }
        var heartbeat = server.has("heartbeatInterval")
                ? parseTimeString(asTimeString(server.get("heartbeatInterval")))
                : Duration.ofSeconds(15);
        var rps = server.has("requestsPerSecond") ? server.get("requestsPerSecond").asInt(0) : 0;
        var burst = server.has("burst") ? server.get("burst").asInt(0) : 0;
        var readBuf = server.has("readBufferSize") ? server.get("readBufferSize").asInt(0) : 0;
        var writeBuf = server.has("writeBufferSize") ? server.get("writeBufferSize").asInt(0) : 0;
        ServerConfig.TlsConfig tls = null;
        if (server.has("tls") && server.get("tls").isObject()) {
            tls = parseTls(server.get("tls"));
        }
        return new ServerConfig(address, port, heartbeat, rps, burst, readBuf, writeBuf, tls);
    }

    private static ServerConfig.TlsConfig parseTls(JsonNode tls) {
        var enabled = tls.has("enabled") && tls.get("enabled").asBoolean(false);
        var certFile = tls.has("certFile") ? tls.get("certFile").asText(null) : null;
        var keyFile = tls.has("keyFile") ? tls.get("keyFile").asText(null) : null;
        var caFile = tls.has("caFile") ? tls.get("caFile").asText(null) : null;
        var clientAuth = tls.has("clientAuth") && tls.get("clientAuth").asBoolean(false);
        Map<String, String> required = null;
        if (tls.has("requiredDNAttributes") && tls.get("requiredDNAttributes").isObject()) {
            required = new LinkedHashMap<>();
            var attrs = tls.get("requiredDNAttributes");
            var it = attrs.fields();
            while (it.hasNext()) {
                var e = it.next();
                required.put(e.getKey(), e.getValue().asText());
            }
        }
        return new ServerConfig.TlsConfig(enabled, certFile, keyFile, caFile, clientAuth, required);
    }

    private static String asTimeString(JsonNode node) {
        return node.isNumber() ? String.valueOf(node.asInt()) : node.asText();
    }

    private static AuthConfig parseAuth(JsonNode auth) {
        var type = auth.has("type") ? auth.get("type").asText() : "token";
        return switch (type) {
            case "basic" -> new AuthConfig.BasicAuth(
                    auth.path("username").asText(""),
                    auth.path("password").asText(""));
            default -> new AuthConfig.TokenAuth(
                    auth.path("token").asText(""),
                    auth.has("header") ? auth.get("header").asText() : null,
                    auth.has("scheme") ? auth.get("scheme").asText() : null);
        };
    }

    public static String httpUrlToWsUrl(String url) {
        var result = url;
        if (result.endsWith("/rest")) {
            result = result.substring(0, result.length() - 5);
        }
        result = result.replaceAll("/+$", "");
        if (result.startsWith("http://")) {
            result = "ws://" + result.substring(7);
        } else if (result.startsWith("https://")) {
            result = "wss://" + result.substring(8);
        }
        if (!result.endsWith("/ws")) {
            result += "/ws";
        }
        return result;
    }

    public static String wsUrlToRestUrl(String wsUrl) {
        var result = wsUrl;
        if (result.endsWith("/ws")) {
            result = result.substring(0, result.length() - 3);
        }
        if (!result.endsWith("/rest")) {
            result += "/rest";
        }
        if (result.startsWith("ws://")) {
            result = "http://" + result.substring(5);
        } else if (result.startsWith("wss://")) {
            result = "https://" + result.substring(6);
        }
        return result;
    }

    public static Duration parseTimeString(String value) {
        if (value == null || value.isEmpty()) {
            return Duration.ofSeconds(2);
        }
        if (value.matches("^\\d+$")) {
            return Duration.ofSeconds(Long.parseLong(value));
        }
        Matcher m = TIME_PATTERN.matcher(value);
        if (m.matches()) {
            long amount = Long.parseLong(m.group(1));
            return switch (m.group(2)) {
                case "ms" -> Duration.ofMillis(amount);
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                default -> Duration.ofSeconds(2);
            };
        }
        return Duration.ofSeconds(2);
    }

    public static class Builder {
        private URI url;
        private ServerConfig server;
        private String providerName;
        private JsonNode providerMetadata;
        private AuthConfig auth;
        private Map<String, String> extraHeaders;
        private Duration reconnectDelay = Duration.ofSeconds(1);
        private Duration maxReconnectDelay = Duration.ofSeconds(30);
        private double reconnectDelayFactor = 2.0;
        private int maxAttempts = 0;
        private Duration heartbeatInterval = Duration.ofSeconds(30);
        private Duration pongTimeout = Duration.ofSeconds(10);
        private Duration resultTimeout = Duration.ofMinutes(2);

        public Builder url(URI url) { this.url = url; return this; }
        public Builder server(ServerConfig s) { this.server = s; return this; }
        public Builder providerName(String n) { this.providerName = n; return this; }
        public Builder providerMetadata(JsonNode m) { this.providerMetadata = m; return this; }
        public Builder auth(AuthConfig a) { this.auth = a; return this; }
        public Builder extraHeaders(Map<String, String> h) { this.extraHeaders = h; return this; }
        public Builder reconnectDelay(Duration d) { this.reconnectDelay = d; return this; }
        public Builder maxReconnectDelay(Duration d) { this.maxReconnectDelay = d; return this; }
        public Builder reconnectDelayFactor(double f) { this.reconnectDelayFactor = f; return this; }
        public Builder maxAttempts(int n) { this.maxAttempts = n; return this; }
        public Builder heartbeatInterval(Duration d) { this.heartbeatInterval = d; return this; }
        public Builder pongTimeout(Duration d) { this.pongTimeout = d; return this; }
        public Builder resultTimeout(Duration d) { this.resultTimeout = d; return this; }

        public RuntimeConfig build() {
            return new RuntimeConfig(url, server, providerName, providerMetadata, auth, extraHeaders,
                    reconnectDelay, maxReconnectDelay, reconnectDelayFactor, maxAttempts,
                    heartbeatInterval, pongTimeout, resultTimeout);
        }
    }
}
