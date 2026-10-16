// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.provider;

import io.kaleido.workflowengine.sdk.handlers.EventProcessor;
import io.kaleido.sdk.core.http.ServiceClient;
import io.kaleido.sdk.core.http.ServiceClientOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HandlerFactoryTest {

    record Config(String queue, int batchSize, List<String> tags) {
    }

    private static HandlerContext context(Map<String, Object> config) {
        return new HandlerContext() {
            @Override
            public String name() {
                return "audit";
            }

            @Override
            public String providerName() {
                return "s-provider1";
            }

            @Override
            public Map<String, Object> config() {
                return config;
            }

            @Override
            public Map<String, String> secrets() {
                return Map.of("dbPassword", "s3cret");
            }

            @Override
            public Map<String, Path> files() {
                return Map.of("truststore.p12", Path.of("/tmp/truststore.p12"));
            }

            @Override
            public ServiceClientOptions serviceClientOptions(String binding, String authRef) {
                return new ServiceClientOptions.Http("http://" + binding + ".local", null, null, null);
            }
        };
    }

    @Test
    void bindsTheConfigToAType() {
        var config = context(Map.of("queue", "payments.in", "batchSize", 50, "tags", List.of("a", "b")))
                .config(Config.class);

        assertEquals(new Config("payments.in", 50, List.of("a", "b")), config);
    }

    @Test
    void aMisspeltKeyOrAMissingPrimitiveFails() {
        assertThrows(IllegalArgumentException.class,
                () -> context(Map.of("queue", "a", "batchSze", 5)).config(Config.class));
        assertThrows(IllegalArgumentException.class, () -> context(Map.of("queue", "a")).config(Config.class));
    }

    @Test
    void configThatDoesNotFitTheTypeFails() {
        var ctx = context(Map.of("batchSize", "lots"));
        assertThrows(IllegalArgumentException.class, () -> ctx.config(Config.class));
    }

    @Test
    void aFactoryCreatesAHandlerFromItsContext() throws Exception {
        HandlerFactory<EventProcessor> factory = ctx -> new EventProcessor() {
            @Override
            public String name() {
                return ctx.name();
            }

            @Override
            public void eventProcessorBatch(io.kaleido.workflowengine.sdk.handlers.RequestContext reqContext,
                                            io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchResult result,
                                            io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchRequest batch) {
            }
        };

        assertEquals("audit", factory.create(context(Map.of())).name());
    }

    @Test
    void serviceClientUsesTheBindingsOptions() {
        assertInstanceOf(ServiceClient.class, context(Map.of()).serviceClient("asset-manager", null));
    }

    @Test
    void readsSecretsAndFilesByName() {
        var ctx = context(Map.of());
        assertEquals("s3cret", ctx.secret("dbPassword"));
        assertEquals(Path.of("/tmp/truststore.p12"), ctx.file("truststore.p12"));

        var error = assertThrows(IllegalArgumentException.class, () -> ctx.secret("apiKey"));
        assertEquals("handler 'audit' has no secret 'apiKey'", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ctx.file("ca.pem"));
    }
}
