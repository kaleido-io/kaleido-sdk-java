// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import io.kaleido.sdk.core.config.ServiceBindingAuth;
import io.kaleido.sdk.core.config.ServiceBindingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ServiceClientOptionsTest {

    private static final ServiceProxy PROXY = (serviceType, id, authRef, method, path, headers, body) -> null;

    @Test
    void aHostedBindingResolvesToTheProxyWithTheAuthRef() {
        var options = ServiceClientOptions.forBinding(
                new ServiceBindingConfig.Hosted("AssetManagerService", "s:am1", 3, 1000), PROXY, "ar-1");

        assertEquals(new ServiceClientOptions.WsProxy(PROXY, "AssetManagerService", "s:am1", "ar-1"), options);
    }

    @Test
    void aHostedBindingNeedsAProxy() {
        var binding = new ServiceBindingConfig.Hosted("AssetManagerService", "s:am1", null, null);

        var error = assertThrows(IllegalStateException.class, () -> ServiceClientOptions.forBinding(binding, null, null));
        assertTrue(error.getMessage().contains("'AssetManagerService' is hosted"), error.getMessage());
    }

    @Test
    void aNonHostedBindingResolvesToHttpAndIgnoresTheProxy() {
        var auth = ServiceBindingAuth.basic("user", "pass");
        var binding = new ServiceBindingConfig.NonHosted("AssetManagerService", "http://am", auth, 2, 1000);

        var expected = new ServiceClientOptions.Http("http://am", auth, 2, 1000);
        assertEquals(expected, ServiceClientOptions.forBinding(binding, null, "ar-1"));
        assertEquals(expected, ServiceClientOptions.forBinding(binding, PROXY, "ar-1"));
    }

    // ── From a config file ──────────────────────────────────────────────────

    @TempDir
    Path dir;

    private Path config(String yaml) throws IOException {
        return Files.writeString(dir.resolve("config.yaml"), yaml);
    }

    private static final String BINDINGS = """
            service-bindings:
              asset-manager:
                bindingType: non-hosted
                serviceType: AssetManagerService
                url: http://localhost:8000
                maxRetries: 2
                timeout: 5000
                auth:
                  type: token
                  token: secret
                  scheme: Bearer
              connector:
                bindingType: hosted
                serviceType: EVMConnectorService
                id: svc-connector-001
            """;

    @Test
    void aNonHostedBindingInAConfigFileResolvesToHttp() throws IOException {
        var expected = new ServiceClientOptions.Http("http://localhost:8000",
                ServiceBindingAuth.token("secret", null, "Bearer"), 2, 5000);

        assertEquals(expected, ServiceClientOptions.fromConfig("asset-manager", config(BINDINGS)));
        assertEquals(expected, ServiceClientOptions.fromConfig("asset-manager",
                config("workflow-engine:\n" + BINDINGS.indent(2))));
    }

    @Test
    void aHostedBindingCannotBeResolvedFromAConfigFile() throws IOException {
        var error = assertThrows(IllegalStateException.class,
                () -> ServiceClientOptions.fromConfig("connector", config(BINDINGS)));
        assertTrue(error.getMessage().contains("'connector' is hosted"), error.getMessage());
    }

    @Test
    void anUnknownBindingNamesTheAvailableOnes() throws IOException {
        var error = assertThrows(IllegalArgumentException.class,
                () -> ServiceClientOptions.fromConfig("key-manager", config(BINDINGS)));
        assertTrue(error.getMessage().contains("Available: asset-manager, connector"), error.getMessage());

        var none = assertThrows(IllegalArgumentException.class,
                () -> ServiceClientOptions.fromConfig("key-manager", config("")));
        assertTrue(none.getMessage().contains("Available: (none)"), none.getMessage());
    }

    @Test
    void resolvingFromTheEnvFailsWhenItIsNotSet() {
        assumeTrue(System.getenv("KALEIDO_CONFIG_FILE") == null, "KALEIDO_CONFIG_FILE is set");

        assertThrows(IllegalStateException.class, () -> ServiceClientOptions.fromConfig("asset-manager"));
    }
}
