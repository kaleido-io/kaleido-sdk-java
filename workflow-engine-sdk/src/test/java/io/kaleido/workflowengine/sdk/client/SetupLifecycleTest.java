// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.client;

import io.kaleido.workflowengine.sdk.app.EventProcessorDef;
import io.kaleido.workflowengine.sdk.app.SetupContext;
import io.kaleido.workflowengine.sdk.app.TransactionHandlerRegistration;
import io.kaleido.workflowengine.sdk.config.ClientConfig;
import io.kaleido.workflowengine.sdk.config.SetupLifecycle;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.handlers.TransactionHandler;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.ProviderCapabilities;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;
import io.kaleido.workflowengine.sdk.runtime.CapturingHandlerRuntime;
import io.kaleido.workflowengine.sdk.runtime.SetupTriggerHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SetupLifecycleTest {

    private static final TransactionHandler NOOP_HANDLER = new TransactionHandler() {
        @Override
        public String name() { return "noop"; }

        @Override
        public void transactionHandlerBatch(RequestContext reqContext,
                                            WSHandleTransactionsResult result,
                                            WSHandleTransactions batch) {
        }
    };

    private static class ObservableRuntime extends CapturingHandlerRuntime {
        final AtomicReference<SetupTriggerHandler> triggerHandler = new AtomicReference<>();
        final AtomicReference<ProviderCapabilities> capabilities = new AtomicReference<>();

        ObservableRuntime(ClientConfig config) {
            super(config);
        }

        @Override
        public void registerSetupTriggerHandler(SetupTriggerHandler handler) {
            triggerHandler.set(handler);
            super.registerSetupTriggerHandler(handler);
        }

        @Override
        public void setProviderCapabilities(ProviderCapabilities providerCapabilities) {
            capabilities.set(providerCapabilities);
            super.setProviderCapabilities(providerCapabilities);
        }
    }

    private static ClientConfig config(SetupLifecycle lifecycle) {
        return ClientConfig.builder()
                .providerName("lifecycle-provider")
                .setupLifecycle(lifecycle)
                .customConfig(JSON.MAPPER.createObjectNode().put("flavor", "vanilla"))
                .build();
    }

    @Test
    void bootModeRunsHooksAtStart() throws Exception {
        var runtime = new ObservableRuntime(config(SetupLifecycle.BOOT));
        var client = new WorkflowEngineClient(config(SetupLifecycle.BOOT), runtime);

        var contexts = new CopyOnWriteArrayList<SetupContext>();
        client.transactionHandler("handler-a",
                TransactionHandlerRegistration.of(NOOP_HANDLER, contexts::add));
        client.eventProcessor("processor-b",
                EventProcessorDef.of((ctx, events) -> {}, contexts::add));

        client.start();

        assertEquals(2, contexts.size());
        assertEquals("handler-a", contexts.get(0).handlerName());
        assertEquals("processor-b", contexts.get(1).handlerName());
        assertEquals("lifecycle-provider", contexts.get(0).providerName());
        assertEquals("vanilla", contexts.get(0).config().get("flavor").asText());
        // Boot mode does not register a trigger handler — stray triggers get the ack-no-op path
        assertNull(runtime.triggerHandler.get());
        assertEquals(Boolean.TRUE, runtime.capabilities.get().hasSetupHooks());
    }

    @Test
    void deferredModeSkipsBootHooksAndRunsOnTrigger() throws Exception {
        var runtime = new ObservableRuntime(config(SetupLifecycle.DEFERRED));
        var client = new WorkflowEngineClient(config(SetupLifecycle.DEFERRED), runtime);

        var setupRuns = new CopyOnWriteArrayList<String>();
        client.transactionHandler("handler-a", TransactionHandlerRegistration.of(NOOP_HANDLER,
                ctx -> setupRuns.add("handler-a")));
        client.transactionHandler("handler-b", TransactionHandlerRegistration.of(NOOP_HANDLER,
                ctx -> {
                    setupRuns.add("handler-b");
                    throw new RuntimeException("setup b failed");
                }));

        client.start();
        assertTrue(setupRuns.isEmpty());

        var triggerHandler = runtime.triggerHandler.get();
        assertNotNull(triggerHandler);

        var errors = triggerHandler.runSetup("auth-ref-1");
        assertEquals(List.of("handler-a", "handler-b"), setupRuns);
        assertEquals(List.of("handler-b: setup b failed"), errors);
    }

    @Test
    void setupRunsHooksWithoutConnecting() throws Exception {
        var runtime = new ObservableRuntime(config(SetupLifecycle.BOOT));
        var client = new WorkflowEngineClient(config(SetupLifecycle.BOOT), runtime);

        var setupRuns = new CopyOnWriteArrayList<String>();
        client.transactionHandler("handler-a", TransactionHandlerRegistration.of(NOOP_HANDLER,
                ctx -> setupRuns.add("handler-a")));

        client.setup();
        assertEquals(List.of("handler-a"), setupRuns);
    }

    @Test
    void capabilitiesReportNoSetupHooks() throws Exception {
        var runtime = new ObservableRuntime(config(SetupLifecycle.BOOT));
        var client = new WorkflowEngineClient(config(SetupLifecycle.BOOT), runtime);
        client.transactionHandler("handler-a", NOOP_HANDLER);

        client.start();
        assertEquals(Boolean.FALSE, runtime.capabilities.get().hasSetupHooks());
    }

    @Test
    void bootHookFailurePropagatesFromStart() {
        var runtime = new ObservableRuntime(config(SetupLifecycle.BOOT));
        var client = new WorkflowEngineClient(config(SetupLifecycle.BOOT), runtime);
        client.transactionHandler("handler-a", TransactionHandlerRegistration.of(NOOP_HANDLER,
                ctx -> {
                    throw new IllegalStateException("boot setup failed");
                }));

        var thrown = assertThrows(IllegalStateException.class, client::start);
        assertEquals("boot setup failed", thrown.getMessage());
    }
}
