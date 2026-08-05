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
                  ws:
                    url: ws://localhost:5503/ws
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
        assertEquals("ws://localhost:5503/ws", config.wsUrl().toString());
        assertEquals("http://localhost:5503", config.restUrl().toString());
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
                  ws:
                    url: wss://example.com/ws
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
                  ws:
                    url: ws://localhost:5503/ws
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
                  ws:
                    url: ws://localhost:5503/ws
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
                  ws:
                    url: ws://localhost:5503/ws
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
                  ws:
                    url: ws://localhost:5503/ws
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
                  ws:
                    url: ws://localhost:5503/ws
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
                  ws:
                    url: ws://localhost:5503/ws
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
                  ws:
                    url: ws://localhost:5503/ws
                  auth: {type: basic, username: u, password: p}
                """);
        assertThrows(Exception.class, () -> ConfigLoader.load(file));
    }

    @Test
    void missingWsUrlAndServerFails(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: broken
                """);
        var e = assertThrows(Exception.class, () -> ConfigLoader.load(file));
        assertTrue(e.getMessage().contains("KA150045"), e.getMessage());
    }

    @Test
    void outboundTlsParsedAndSatisfiesCredentialCheck(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  url: https://wfe:5503
                  ws:
                    url: wss://wfe:5503/handler/ws
                  providerName: mtls-provider
                  tls:
                    enabled: true
                    caFile: /etc/tls/ca.crt
                    certFile: /etc/tls/tls.crt
                    keyFile: /etc/tls/tls.key
                """);

        var config = ConfigLoader.load(file);
        assertEquals("wss://wfe:5503/handler/ws", config.wsUrl().toString());
        assertEquals("https://wfe:5503", config.restUrl().toString());
        assertNull(config.auth(), "mutual TLS is the credential; no auth block is required");
        assertNotNull(config.tls());
        assertTrue(config.tls().enabled());
        assertEquals("/etc/tls/ca.crt", config.tls().caFile());
        assertEquals("/etc/tls/tls.crt", config.tls().certFile());
        assertEquals("/etc/tls/tls.key", config.tls().keyFile());
        assertTrue(config.tls().hasIdentity());
        assertFalse(config.tls().insecureSkipHostVerify());
    }

    @Test
    void wsUrlAloneIsEnough(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: ws-only
                  ws:
                    url: wss://wfe:5503/handler/ws
                  auth: {type: token, token: t}
                """);

        var config = ConfigLoader.load(file);
        assertEquals("wss://wfe:5503/handler/ws", config.wsUrl().toString());
        assertNull(config.restUrl(), "no REST base is invented from the WebSocket URL");
    }

    @Test
    void wsAndRestUrlsNeedNoRelationship(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: no-guessing
                  url: https://wfe:5503/rest
                  ws:
                    url: wss://elsewhere:9999/some/other/path
                  auth: {type: token, token: t}
                """);

        var config = ConfigLoader.load(file);
        assertEquals("wss://elsewhere:9999/some/other/path", config.wsUrl().toString());
        assertEquals("https://wfe:5503/rest", config.restUrl().toString());
    }

    /** A REST base is not a WebSocket endpoint, and outbound mode will not invent one. */
    @Test
    void restUrlAloneIsNotEnoughForOutbound(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: rest-only
                  url: https://wfe:5503
                  auth: {type: token, token: t}
                """);
        var e = assertThrows(Exception.class, () -> ConfigLoader.load(file));
        assertTrue(e.getMessage().contains("KA150045"), e.getMessage());
    }

    /** The REST base is orthogonal to direction: an inbound provider can still call the API. */
    @Test
    void inboundKeepsRestUrl(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: inbound-with-rest
                  url: https://wfe:5503
                  server:
                    port: 6001
                """);

        var config = ConfigLoader.load(file);
        assertNotNull(config.server());
        assertNull(config.wsUrl());
        assertEquals("https://wfe:5503", config.restUrl().toString());
    }

    @Test
    void outboundWithoutAnyCredentialFails(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  url: http://wfe:5503
                  ws:
                    url: ws://wfe:5503/handler/ws
                  providerName: anonymous
                """);
        var e = assertThrows(Exception.class, () -> ConfigLoader.load(file));
        assertTrue(e.getMessage().contains("KA150044"), e.getMessage());
    }

    @Test
    void outboundTlsWithoutClientCertStillNeedsAuth(@TempDir Path tempDir) throws Exception {
        // A CA verifies the server; it says nothing about who this client is.
        var file = write(tempDir, """
                workflow-engine:
                  url: https://wfe:5503
                  ws:
                    url: wss://wfe:5503/handler/ws
                  providerName: server-auth-only
                  tls:
                    enabled: true
                    caFile: /etc/tls/ca.crt
                """);
        var e = assertThrows(Exception.class, () -> ConfigLoader.load(file));
        assertTrue(e.getMessage().contains("KA150044"), e.getMessage());
    }

    @Test
    void outboundTlsIgnoredWhenNotEnabled(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  url: https://wfe:5503
                  ws:
                    url: wss://wfe:5503/handler/ws
                  providerName: disabled-tls
                  tls:
                    enabled: false
                    certFile: /etc/tls/tls.crt
                    keyFile: /etc/tls/tls.key
                  auth: {type: token, token: t}
                """);

        var config = ConfigLoader.load(file);
        assertNull(config.tls());
        assertNotNull(config.auth());
    }

    @Test
    void outboundInsecureSkipHostVerifyParsed(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  url: https://wfe:5503
                  ws:
                    url: wss://wfe:5503/handler/ws
                  providerName: skip-host-verify
                  tls:
                    enabled: true
                    certFile: /etc/tls/tls.crt
                    keyFile: /etc/tls/tls.key
                    insecureSkipHostVerify: true
                """);

        var config = ConfigLoader.load(file);
        assertTrue(config.tls().insecureSkipHostVerify());
    }

    @Test
    void outboundAcceptsAuthAndTlsTogether(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  url: https://wfe:5503
                  ws:
                    url: wss://wfe:5503/handler/ws
                  providerName: both
                  auth: {type: token, token: my-secret}
                  tls:
                    enabled: true
                    caFile: /etc/tls/ca.crt
                    certFile: /etc/tls/tls.crt
                    keyFile: /etc/tls/tls.key
                """);

        var config = ConfigLoader.load(file);
        assertInstanceOf(AuthConfig.TokenAuth.class, config.auth());
        assertNotNull(config.tls());
    }

    @Test
    void inboundNeedsNoCredential(@TempDir Path tempDir) throws Exception {
        // The credential check is outbound-only: inbound, the engine dials in and
        // authenticates itself via server.tls.clientAuth.
        var file = write(tempDir, """
                workflow-engine:
                  providerName: inbound-no-cred
                  server:
                    port: 6001
                """);

        var config = ConfigLoader.load(file);
        assertNull(config.auth());
        assertNull(config.tls());
        assertNotNull(config.server());
    }

    @Test
    void inboundServerConfigParsed(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: inbound-provider
                  server:
                    address: 0.0.0.0
                    port: 6001
                    heartbeatInterval: 45s
                """);

        var config = ConfigLoader.load(file);
        assertNull(config.wsUrl());
        assertNull(config.auth());
        assertNotNull(config.server());
        assertEquals("0.0.0.0", config.server().address());
        assertEquals(6001, config.server().port());
        assertEquals(Duration.ofSeconds(45), config.heartbeatInterval());
    }

    @Test
    void inboundServerConfigDefaultsPortAndTls() throws Exception {
        var server = new ServerConfig(null, null, null);
        assertEquals(ServerConfig.DEFAULT_PORT, server.resolvedPort());

        var withPort = new ServerConfig("127.0.0.1", 7000, null);
        assertEquals(7000, withPort.resolvedPort());
    }

    @Test
    void inboundServerTlsConfigParsed(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: inbound-tls-provider
                  server:
                    address: 0.0.0.0
                    port: 6443
                    tls:
                      enabled: true
                      certFile: /etc/tls/cert.pem
                      keyFile: /etc/tls/key.pem
                """);

        var config = ConfigLoader.load(file);
        assertNotNull(config.server().tls());
        assertTrue(config.server().tls().enabled());
        assertEquals("/etc/tls/cert.pem", config.server().tls().certFile());
        assertEquals("/etc/tls/key.pem", config.server().tls().keyFile());
    }

    @Test
    void customConfigFallsBackToConfigKey(@TempDir Path tempDir) throws Exception {
        var file = write(tempDir, """
                workflow-engine:
                  providerName: custom-config
                  ws:
                    url: ws://localhost:5503/ws
                  auth: {type: basic, username: u, password: p}
                config:
                  mySetting: enabled
                """);

        var custom = ConfigLoader.load(file).customConfig();
        assertNotNull(custom);
        assertEquals("enabled", custom.get("mySetting").asText());
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
