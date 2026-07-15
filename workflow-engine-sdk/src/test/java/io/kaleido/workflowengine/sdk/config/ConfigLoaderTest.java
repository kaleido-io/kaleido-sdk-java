// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.config;

import io.kaleido.workflowengine.sdk.service.ServiceBindingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    private static Path write(Path dir, String yaml) throws Exception {
        var file = dir.resolve("config.yaml");
        Files.writeString(file, yaml);
        return file;
    }

    @Test
    void builderDefaults() {
        var config = ClientConfig.builder().providerName("test").build();
        assertEquals("test", config.providerName());
        assertEquals(Duration.ofSeconds(1), config.reconnectDelay());
        assertEquals(Duration.ofSeconds(30), config.maxReconnectDelay());
        assertEquals(2.0, config.reconnectDelayFactor());
        assertEquals(SetupLifecycle.BOOT, config.setupLifecycle());
        assertTrue(config.serviceBindings().isEmpty());
    }

    @Test
    void parseTokenAuth(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
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
                """);

        var config = ConfigLoader.load(file);
        assertEquals("my-provider", config.providerName());
        assertEquals("ws://localhost:5503/ws", config.url().toString());
        assertEquals(5, config.maxAttempts());
        assertEquals(Duration.ofSeconds(3), config.reconnectDelay());

        var tokenAuth = assertInstanceOf(AuthConfig.TokenAuth.class, config.auth());
        assertEquals("my-secret", tokenAuth.token());
        assertEquals("X-Auth", tokenAuth.headerName());
        assertEquals("Bearer my-secret", tokenAuth.resolveHeaderValue());
    }

    @Test
    void parseBasicAuth(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  url: https://example.com
                  providerName: basic-provider
                  auth:
                    type: basic
                    username: user
                    password: pass
                """);

        var config = ConfigLoader.load(file);
        assertInstanceOf(AuthConfig.BasicAuth.class, config.auth());
        assertEquals("Authorization", config.auth().resolveHeaderName());
        assertTrue(config.auth().resolveHeaderValue().startsWith("Basic "));
    }

    @Test
    void parseProviderMetadata(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: meta-provider
                  providerMetadata:
                    displayName: My Provider
                  url: http://localhost:5503
                  auth: {type: basic, username: u, password: p}
                """);

        var config = ConfigLoader.load(file);
        assertEquals("My Provider", config.providerMetadata().get("displayName").asText());
    }

    @Test
    void parseNumericRetryDelayAsSeconds(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: retry-provider
                  url: http://localhost:5503
                  auth: {type: basic, username: u, password: p}
                  retryDelay: 3
                """);
        assertEquals(Duration.ofSeconds(3), ConfigLoader.load(file).reconnectDelay());
    }

    @Test
    void setupLifecycleParsing(@TempDir Path tempDir) throws Exception {
        var base = """
                workflow-engine:
                  providerName: lifecycle-provider
                  url: http://localhost:5503
                  auth: {type: basic, username: u, password: p}
                """;

        assertEquals(SetupLifecycle.BOOT, ConfigLoader.load(write(tempDir, base)).setupLifecycle());
        assertEquals(SetupLifecycle.BOOT,
                ConfigLoader.load(write(tempDir, base + "  setupLifecycle: boot\n")).setupLifecycle());
        assertEquals(SetupLifecycle.DEFERRED,
                ConfigLoader.load(write(tempDir, base + "  setupLifecycle: deferred\n")).setupLifecycle());
        // Unknown value falls back to the default rather than failing the load
        assertEquals(SetupLifecycle.BOOT,
                ConfigLoader.load(write(tempDir, base + "  setupLifecycle: sometimes\n")).setupLifecycle());
    }

    @Test
    void serviceBindingsTopLevel(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: bindings-provider
                  url: http://localhost:5503
                  auth: {type: basic, username: u, password: p}
                service-bindings:
                  asset-manager:
                    bindingType: hosted
                    type: asset-manager
                    id: svc-123
                  key-manager:
                    bindingType: non-hosted
                    url: http://localhost:8000
                    auth: {type: token, token: secret, header: X-Api-Key}
                    maxRetries: 4
                    timeout: 15000
                """);

        var bindings = ConfigLoader.load(file).serviceBindings();
        assertEquals(2, bindings.size());

        var hosted = assertInstanceOf(ServiceBindingConfig.Hosted.class, bindings.get("asset-manager"));
        assertEquals("svc-123", hosted.id());
        assertEquals("asset-manager", hosted.type());

        var nonHosted = assertInstanceOf(ServiceBindingConfig.NonHosted.class, bindings.get("key-manager"));
        assertEquals("http://localhost:8000", nonHosted.url());
        assertEquals("secret", nonHosted.auth().token());
        assertEquals("X-Api-Key", nonHosted.auth().header());
        assertEquals(4, nonHosted.maxRetries());
        assertEquals(15000, nonHosted.timeout());
    }

    @Test
    void serviceBindingsNestedUnderWorkflowEngine(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: nested-bindings
                  url: http://localhost:5503
                  auth: {type: basic, username: u, password: p}
                  service-bindings:
                    apigw:
                      bindingType: hosted
                      id: svc-apigw
                """);

        var bindings = ConfigLoader.load(file).serviceBindings();
        assertEquals(1, bindings.size());
        // service type defaults to the binding name when omitted
        assertEquals("apigw", bindings.get("apigw").type());
    }

    @Test
    void invalidBindingsSkipped(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: skip-bindings
                  url: http://localhost:5503
                  auth: {type: basic, username: u, password: p}
                service-bindings:
                  no-id-hosted:
                    bindingType: hosted
                  no-url:
                    bindingType: non-hosted
                    auth: {type: basic, username: u, password: p}
                  no-auth:
                    bindingType: non-hosted
                    url: http://localhost:9999
                  good:
                    bindingType: hosted
                    id: svc-good
                """);

        var bindings = ConfigLoader.load(file).serviceBindings();
        assertEquals(1, bindings.size());
        assertNotNull(bindings.get("good"));
    }

    @Test
    void missingProviderNameFails(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  url: http://localhost:5503
                  auth: {type: basic, username: u, password: p}
                """);
        assertThrows(Exception.class, () -> ConfigLoader.load(file));
    }

    @Test
    void missingUrlAndAuthFails(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: broken
                """);
        assertThrows(Exception.class, () -> ConfigLoader.load(file));
    }

    @Test
    void customConfigFallsBackToConfigKey(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: custom-config
                  url: http://localhost:5503
                  auth: {type: basic, username: u, password: p}
                config:
                  mySetting: enabled
                """);

        var custom = ConfigLoader.load(file).customConfig();
        assertNotNull(custom);
        assertEquals("enabled", custom.get("mySetting").asText());
    }

    @Test
    void urlConversions() {
        assertEquals("ws://localhost:5503/ws", ConfigLoader.httpUrlToWsUrl("http://localhost:5503"));
        assertEquals("wss://example.com/ws", ConfigLoader.httpUrlToWsUrl("https://example.com"));
        assertEquals("ws://localhost:5503/ws", ConfigLoader.httpUrlToWsUrl("ws://localhost:5503/ws"));
        assertEquals("ws://localhost/ws", ConfigLoader.httpUrlToWsUrl("http://localhost/"));
        assertEquals("wss://acct.kaleido.io/endpoint/env/wfe/ws",
                ConfigLoader.httpUrlToWsUrl("https://acct.kaleido.io/endpoint/env/wfe/rest"));

        assertEquals("http://localhost:5503/rest", ConfigLoader.wsUrlToRestUrl("ws://localhost:5503/ws"));
        assertEquals("https://example.com/rest", ConfigLoader.wsUrlToRestUrl("wss://example.com/ws"));
    }

    @Test
    void timeStringParsing() {
        assertEquals(Duration.ofSeconds(2), ConfigLoader.parseTimeString(""));
        assertEquals(Duration.ofSeconds(5), ConfigLoader.parseTimeString("5"));
        assertEquals(Duration.ofMillis(500), ConfigLoader.parseTimeString("500ms"));
        assertEquals(Duration.ofSeconds(3), ConfigLoader.parseTimeString("3s"));
        assertEquals(Duration.ofMinutes(1), ConfigLoader.parseTimeString("1m"));
        assertEquals(Duration.ofHours(2), ConfigLoader.parseTimeString("2h"));
        assertEquals(Duration.ofSeconds(2), ConfigLoader.parseTimeString("nonsense"));
    }
}
