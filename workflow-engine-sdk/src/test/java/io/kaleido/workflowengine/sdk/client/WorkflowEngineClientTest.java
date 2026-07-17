// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.client;

import io.kaleido.workflowengine.sdk.app.EventProcessorDef;
import io.kaleido.workflowengine.sdk.config.ClientConfig;
import io.kaleido.workflowengine.sdk.errors.SDKException;
import io.kaleido.workflowengine.sdk.factories.EventSourceFactory;
import io.kaleido.workflowengine.sdk.factories.EventSourcePollOutput;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.handlers.TransactionHandler;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;
import io.kaleido.workflowengine.sdk.service.ServiceBindingAuth;
import io.kaleido.workflowengine.sdk.service.ServiceBindingConfig;
import io.kaleido.workflowengine.sdk.service.ServiceClientOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowEngineClientTest {

    private static final TransactionHandler NOOP_HANDLER = new TransactionHandler() {
        @Override
        public String name() { return "noop"; }

        @Override
        public void transactionHandlerBatch(RequestContext reqContext,
                                            WSHandleTransactionsResult result,
                                            WSHandleTransactions batch) {
        }
    };

    private static ClientConfig configWithBindings() {
        return ClientConfig.builder()
                .providerName("test-provider")
                .serviceBindings(Map.of(
                        "asset-manager", new ServiceBindingConfig.Hosted("asset-manager", "svc-123", null, null),
                        "key-manager", new ServiceBindingConfig.NonHosted("key-manager", "http://localhost:8000",
                                ServiceBindingAuth.basic("user", "pass"), 3, 30000)))
                .build();
    }

    @Test
    void duplicateHandlerNamesRejected() {
        var client = new WorkflowEngineClient(ClientConfig.builder().providerName("p").build());
        client.transactionHandler("handler-1", NOOP_HANDLER);

        assertThrows(SDKException.class, () -> client.transactionHandler("handler-1", NOOP_HANDLER));
        assertThrows(SDKException.class, () -> client.eventProcessor("handler-1",
                EventProcessorDef.of((ctx, events) -> {})));
        assertThrows(SDKException.class, () -> client.eventSource(
                EventSourceFactory.createEventSource("handler-1",
                        (conf, cp, authRef) -> EventSourcePollOutput.of(Map.of(), List.of()))));
    }

    @Test
    void getServiceBindingResolvesAndCopies() {
        var client = new WorkflowEngineClient(configWithBindings());

        var binding = client.getServiceBinding("asset-manager");
        assertInstanceOf(ServiceBindingConfig.Hosted.class, binding);

        var bindings = client.getServiceBindings();
        bindings.remove("asset-manager");
        assertNotNull(client.getServiceBinding("asset-manager"));
    }

    @Test
    void unknownServiceBindingListsAvailable() {
        var client = new WorkflowEngineClient(configWithBindings());
        var thrown = assertThrows(SDKException.class, () -> client.getServiceBinding("nope"));
        assertTrue(thrown.getMessage().contains("asset-manager"));
    }

    @Test
    void serviceClientOptionsForHostedBinding() {
        var client = new WorkflowEngineClient(configWithBindings());
        var options = client.getServiceClientOptions("asset-manager", "auth-ref-1");

        var wsProxy = assertInstanceOf(ServiceClientOptions.WsProxy.class, options);
        assertEquals("asset-manager", wsProxy.serviceType());
        assertEquals("svc-123", wsProxy.id());
        assertEquals("auth-ref-1", wsProxy.authRef());
        assertSame(client.getWSProxyAdapter(), wsProxy.wsProxy());
    }

    @Test
    void serviceClientOptionsForNonHostedBinding() {
        var client = new WorkflowEngineClient(configWithBindings());
        var options = client.getServiceClientOptions("key-manager");

        var http = assertInstanceOf(ServiceClientOptions.Http.class, options);
        assertEquals("http://localhost:8000", http.url());
        assertEquals("user", http.auth().username());
        assertEquals(3, http.maxRetries());
        assertEquals(30000, http.timeout());
    }

    @Test
    void registerHandlerDispatchesByType() {
        var client = new WorkflowEngineClient(ClientConfig.builder().providerName("p").build());
        client.registerHandler(NOOP_HANDLER);
        client.registerHandler(EventSourceFactory.createEventSource("source-1",
                (conf, cp, authRef) -> EventSourcePollOutput.of(Map.of(), List.of())));
    }
}
