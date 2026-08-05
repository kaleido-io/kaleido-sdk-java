// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.componenttest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kaleido.workflowengine.sdk.config.AuthConfig;
import io.kaleido.workflowengine.sdk.config.ClientConfig;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Component test configuration. Loads {@code test-config.yaml} from the test
 * classpath when present (same shape as the TS suite's config file), otherwise
 * falls back to local development defaults ({@code http://localhost:5503}).
 * Env vars override either source: {@code FLOW_ENGINE_URL},
 * {@code WORKFLOW_ENGINE_AUTH_TOKEN}, {@code WORKFLOW_ENGINE_AUTH_HEADER},
 * {@code WORKFLOW_ENGINE_AUTH_SCHEME}.
 */
final class TestConfig {
    private TestConfig() {}

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static final JsonNode FILE_CONFIG = loadFileConfig();

    private static JsonNode loadFileConfig() {
        try (var stream = TestConfig.class.getClassLoader().getResourceAsStream("test-config.yaml")) {
            return stream != null ? YAML.readTree(stream).path("workflowEngine") : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test-config.yaml", e);
        }
    }

    private static String fromEnvFileOrDefault(String envVar, String filePath, String fallback) {
        var env = System.getenv(envVar);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        if (FILE_CONFIG != null) {
            var node = FILE_CONFIG.at(filePath);
            if (node.isTextual()) {
                return node.asText();
            }
        }
        return fallback;
    }

    /** Base HTTP URL of the workflow engine's REST API. */
    static String flowEngineUrl() {
        return fromEnvFileOrDefault("FLOW_ENGINE_URL", "/url", "http://localhost:5503");
    }

    /** WebSocket endpoint of the workflow engine's provider socket. */
    static String flowEngineWsUrl() {
        return fromEnvFileOrDefault("FLOW_ENGINE_WS_URL", "/ws/url", "ws://localhost:5503/ws");
    }

    static String authToken() {
        return fromEnvFileOrDefault("WORKFLOW_ENGINE_AUTH_TOKEN", "/auth/token", "dev-token-123");
    }

    static String authHeaderName() {
        return fromEnvFileOrDefault("WORKFLOW_ENGINE_AUTH_HEADER", "/auth/header", "X-Kld-Authz");
    }

    static String authScheme() {
        return fromEnvFileOrDefault("WORKFLOW_ENGINE_AUTH_SCHEME", "/auth/scheme", "");
    }

    /** The auth header for direct REST API calls. */
    static Map<String, String> authHeaders() {
        var auth = clientAuth();
        return Map.of(auth.resolveHeaderName(), auth.resolveHeaderValue());
    }

    private static AuthConfig clientAuth() {
        if (FILE_CONFIG != null && "basic".equals(FILE_CONFIG.at("/auth/type").asText())) {
            var username = FILE_CONFIG.at("/auth/username").asText("");
            var password = FILE_CONFIG.at("/auth/password").asText("");
            return new AuthConfig.BasicAuth(username, password);
        }
        return new AuthConfig.TokenAuth(authToken(), authHeaderName(), authScheme());
    }

    /** SDK client config for a WebSocket provider connection. */
    static ClientConfig clientConfig(String providerName) {
        return ClientConfig.builder()
                .wsUrl(URI.create(flowEngineWsUrl()))
                .restUrl(URI.create(flowEngineUrl()))
                .providerName(providerName)
                .auth(clientAuth())
                .reconnectDelay(Duration.ofSeconds(2))
                .build();
    }

    static String basicAuthValue(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }
}
