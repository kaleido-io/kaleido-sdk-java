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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ClientConfig(
        URI url,
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

    public static ClientConfig fromYaml(Path file) {
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

    public static ClientConfig fromYaml(java.io.InputStream inputStream) {
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

    public static ClientConfig fromEnv() {
        var configFile = System.getenv(ENV_CONFIG_FILE);
        if (configFile == null || configFile.isEmpty()) {
            throw SDKErrors.error(SDKErrors.CONFIG_FILE_NOT_SET,
                    ENV_CONFIG_FILE + " environment variable is not set");
        }
        return fromYaml(Path.of(configFile));
    }

    private static ClientConfig parseYamlNode(JsonNode wfe) {
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
        var hasAuth = wfe.has("auth") && wfe.get("auth").isObject();
        var hasServer = wfe.has("server") && wfe.get("server").isObject();

        if (!urlStr.isEmpty() && hasAuth) {
            b.url(URI.create(httpUrlToWsUrl(urlStr)));
            b.auth(parseAuth(wfe.get("auth")));
        } else if (hasServer) {
            // Inbound/hosted mode -- no URL, no auth needed from YAML
        } else if (!urlStr.isEmpty()) {
            throw SDKErrors.error(SDKErrors.CONFIG_FILE_READ_FAILED,
                    "Missing url or auth in workflow-engine config");
        } else {
            throw SDKErrors.error(SDKErrors.CONFIG_FILE_READ_FAILED,
                    "Missing url or auth in workflow-engine config");
        }

        return b.build();
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

        public ClientConfig build() {
            return new ClientConfig(url, providerName, providerMetadata, auth, extraHeaders,
                    reconnectDelay, maxReconnectDelay, reconnectDelayFactor, maxAttempts,
                    heartbeatInterval, pongTimeout, resultTimeout);
        }
    }
}
