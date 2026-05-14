// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.config;

import io.kaleido.wfe.sdk.errors.SDKException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ClientConfigTest {

    @Test
    void builderDefaults() {
        var config = RuntimeConfig.builder()
                .providerName("test")
                .build();

        assertEquals("test", config.providerName());
        assertEquals(Duration.ofSeconds(1), config.reconnectDelay());
        assertEquals(Duration.ofSeconds(30), config.maxReconnectDelay());
        assertEquals(2.0, config.reconnectDelayFactor());
    }

    @Test
    void parseYaml(@TempDir Path tempDir) throws Exception {
        var yaml = """
                workflow-engine:
                  url: http://localhost:5503
                  providerName: my-provider
                  auth:
                    type: token
                    token: my-secret
                    header: X-Auth
                    scheme: Bearer
                  maxRetries: 5
                  retryDelay: 3s
                """;
        var file = tempDir.resolve("config.yaml");
        Files.writeString(file, yaml);

        var config = RuntimeConfig.fromYaml(file);
        assertEquals("my-provider", config.providerName());
        assertNotNull(config.url());
        assertTrue(config.url().toString().endsWith("/ws"));
        assertTrue(config.url().toString().startsWith("ws://"));
        assertEquals(5, config.maxAttempts());
        assertEquals(Duration.ofSeconds(3), config.reconnectDelay());

        assertInstanceOf(AuthConfig.TokenAuth.class, config.auth());
        var tokenAuth = (AuthConfig.TokenAuth) config.auth();
        assertEquals("my-secret", tokenAuth.token());
        assertEquals("X-Auth", tokenAuth.headerName());
        assertEquals("Bearer", tokenAuth.scheme());
        assertEquals("Bearer my-secret", tokenAuth.resolveHeaderValue());
    }

    @Test
    void parseYamlBasicAuth(@TempDir Path tempDir) throws Exception {
        var yaml = """
                workflow-engine:
                  url: https://example.com
                  providerName: basic-provider
                  auth:
                    type: basic
                    username: user
                    password: pass
                """;
        var file = tempDir.resolve("config.yaml");
        Files.writeString(file, yaml);

        var config = RuntimeConfig.fromYaml(file);
        assertInstanceOf(AuthConfig.BasicAuth.class, config.auth());
        assertEquals("Authorization", config.auth().resolveHeaderName());
        assertTrue(config.auth().resolveHeaderValue().startsWith("Basic "));
    }

    @Test
    void parseYamlWithProviderMetadata(@TempDir Path tempDir) throws Exception {
        var yaml = """
                workflow-engine:
                  providerName: meta-provider
                  providerMetadata:
                    displayName: My Provider
                    description: A test provider
                  url: http://localhost:5503
                  auth:
                    type: basic
                    username: u
                    password: p
                """;
        var file = tempDir.resolve("config.yaml");
        Files.writeString(file, yaml);

        var config = RuntimeConfig.fromYaml(file);
        assertNotNull(config.providerMetadata());
        assertEquals("My Provider", config.providerMetadata().get("displayName").asText());
        assertEquals("A test provider", config.providerMetadata().get("description").asText());
    }

    @Test
    void parseYamlNumericRetryDelay(@TempDir Path tempDir) throws Exception {
        var yaml = """
                workflow-engine:
                  providerName: retry-provider
                  url: http://localhost:5503
                  auth:
                    type: basic
                    username: u
                    password: p
                  retryDelay: 3
                """;
        var file = tempDir.resolve("config.yaml");
        Files.writeString(file, yaml);

        var config = RuntimeConfig.fromYaml(file);
        assertEquals(Duration.ofSeconds(3), config.reconnectDelay());
    }

    @Test
    void parseYamlMissingProviderName(@TempDir Path tempDir) throws Exception {
        var yaml = """
                workflow-engine:
                  url: http://localhost:5503
                  auth:
                    type: basic
                    username: u
                    password: p
                """;
        var file = tempDir.resolve("config.yaml");
        Files.writeString(file, yaml);

        assertThrows(Exception.class, () -> RuntimeConfig.fromYaml(file));
    }

    @Test
    void parseYamlMissingUrlAndAuth(@TempDir Path tempDir) throws Exception {
        var yaml = """
                workflow-engine:
                  providerName: broken
                """;
        var file = tempDir.resolve("config.yaml");
        Files.writeString(file, yaml);

        assertThrows(Exception.class, () -> RuntimeConfig.fromYaml(file));
    }

    @Test
    void parseYamlRestUrl(@TempDir Path tempDir) throws Exception {
        var yaml = """
                workflow-engine:
                  providerName: rest-url-provider
                  url: https://my-account.kaleido.io/endpoint/env1/wfe1/rest
                  auth:
                    type: basic
                    username: u
                    password: p
                """;
        var file = tempDir.resolve("config.yaml");
        Files.writeString(file, yaml);

        var config = RuntimeConfig.fromYaml(file);
        assertEquals("wss://my-account.kaleido.io/endpoint/env1/wfe1/ws", config.url().toString());
    }

    @Test
    void parseYamlServerMode(@TempDir Path tempDir) throws Exception {
        var yaml = """
                workflow-engine:
                  providerName: server-provider
                  server:
                    address: 0.0.0.0
                    port: 9876
                    heartbeatInterval: 15s
                    requestsPerSecond: 100
                    burst: 10
                    tls:
                      enabled: true
                      certFile: /etc/ssl/cert.pem
                      keyFile: /etc/ssl/key.pem
                      caFile: /etc/ssl/ca.pem
                      clientAuth: true
                      requiredDNAttributes:
                        CN: workflow-engine
                """;
        var file = tempDir.resolve("config.yaml");
        Files.writeString(file, yaml);

        var config = RuntimeConfig.fromYaml(file);
        assertNotNull(config.server());
        assertNull(config.url());
        assertEquals("0.0.0.0", config.server().address());
        assertEquals(9876, config.server().port());
        assertEquals(Duration.ofSeconds(15), config.server().heartbeatInterval());
        assertEquals(100, config.server().requestsPerSecond());
        assertEquals(10, config.server().burst());
        assertNotNull(config.server().tls());
        assertTrue(config.server().tls().enabled());
        assertTrue(config.server().tls().clientAuth());
        assertEquals("/etc/ssl/ca.pem", config.server().tls().caFile());
        assertEquals("workflow-engine", config.server().tls().requiredDNAttributes().get("CN"));
    }

    @Test
    void parseYamlRejectsUrlAndServerTogether(@TempDir Path tempDir) throws Exception {
        var yaml = """
                workflow-engine:
                  providerName: dual-mode
                  url: http://localhost:5503
                  server:
                    port: 9876
                """;
        var file = tempDir.resolve("config.yaml");
        Files.writeString(file, yaml);

        var ex = assertThrows(SDKException.class, () -> RuntimeConfig.fromYaml(file));
        assertEquals("KA150050", ex.code());
    }

    @Test
    void httpUrlToWsUrl() {
        assertEquals("ws://localhost:5503/ws", RuntimeConfig.httpUrlToWsUrl("http://localhost:5503"));
        assertEquals("wss://example.com/ws", RuntimeConfig.httpUrlToWsUrl("https://example.com"));
        assertEquals("ws://localhost:5503/ws", RuntimeConfig.httpUrlToWsUrl("ws://localhost:5503/ws"));
        assertEquals("ws://localhost/ws", RuntimeConfig.httpUrlToWsUrl("http://localhost/"));
        assertEquals("wss://acct.kaleido.io/endpoint/env/wfe/ws",
                RuntimeConfig.httpUrlToWsUrl("https://acct.kaleido.io/endpoint/env/wfe/rest"));
    }

    @Test
    void wsUrlToRestUrl() {
        assertEquals("http://localhost:5503/rest", RuntimeConfig.wsUrlToRestUrl("ws://localhost:5503/ws"));
        assertEquals("https://example.com/rest", RuntimeConfig.wsUrlToRestUrl("wss://example.com/ws"));
    }

    @Test
    void parseTimeString() {
        assertEquals(Duration.ofSeconds(2), RuntimeConfig.parseTimeString(""));
        assertEquals(Duration.ofSeconds(5), RuntimeConfig.parseTimeString("5"));
        assertEquals(Duration.ofMillis(500), RuntimeConfig.parseTimeString("500ms"));
        assertEquals(Duration.ofSeconds(3), RuntimeConfig.parseTimeString("3s"));
        assertEquals(Duration.ofMinutes(1), RuntimeConfig.parseTimeString("1m"));
        assertEquals(Duration.ofHours(2), RuntimeConfig.parseTimeString("2h"));
    }
}
