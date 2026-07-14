// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import io.kaleido.workflowengine.sdk.handlers.EventProcessor;
import io.kaleido.workflowengine.sdk.handlers.EventSource;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.handlers.TransactionHandler;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.ListenerEvent;
import io.kaleido.workflowengine.sdk.protocol.WSEvaluateReplyResult;
import io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchResult;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceConfig;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceDeleteRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceDeleteResult;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceValidateConfigRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceValidateConfigResult;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;
import io.kaleido.workflowengine.sdk.protocol.WSListenerPollRequest;
import io.kaleido.workflowengine.sdk.protocol.WSListenerPollResult;
import io.kaleido.workflowengine.sdk.protocol.WSSetupTriggerResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HandlerRuntimeTest {

    private final CapturingHandlerRuntime runtime = new CapturingHandlerRuntime();

    private static String transactionsJson() {
        return """
                {"messageType":"handle_transactions","id":"batch-1","handler":"txn-handler",
                 "transactions":[
                   {"transactionId":"txn-1","workflowId":"wf-1","stage":"init","input":{"hello":"world"}},
                   {"transactionId":"txn-2","workflowId":"wf-1","stage":"init","input":{"hello":"again"}}
                 ]}
                """;
    }

    @Test
    void transactionDispatchSendsMutatedResult() throws Exception {
        var seenContext = new AtomicReference<RequestContext>();
        runtime.registerTransactionHandler("txn-handler", new TransactionHandler() {
            @Override
            public String name() { return "txn-handler"; }

            @Override
            public void transactionHandlerBatch(RequestContext reqContext,
                                                WSHandleTransactionsResult result,
                                                WSHandleTransactions batch) {
                seenContext.set(reqContext);
                for (var txn : batch.transactions()) {
                    result.getResults().add(WSEvaluateReplyResult.stage("done-" + txn.transactionId()));
                }
            }
        });

        var response = runtime.roundTrip(transactionsJson(), WSHandleTransactionsResult.class);

        assertEquals("batch-1", response.getId());
        assertEquals("txn-handler", response.getHandler());
        assertNull(response.getError());
        assertEquals(List.of("done-txn-1", "done-txn-2"),
                response.getResults().stream().map(WSEvaluateReplyResult::getStage).toList());

        assertEquals("batch-1", seenContext.get().requestId());
        // The runtime cancels the context after the dispatch completes
        assertTrue(seenContext.get().signal().isCancelled());
    }

    @Test
    void transactionHandlerThrowFillsPerTransactionErrors() throws Exception {
        runtime.registerTransactionHandler("txn-handler", new TransactionHandler() {
            @Override
            public String name() { return "txn-handler"; }

            @Override
            public void transactionHandlerBatch(RequestContext reqContext,
                                                WSHandleTransactionsResult result,
                                                WSHandleTransactions batch) {
                // partial mutation before the failure — the error fill replaces it
                result.getResults().add(WSEvaluateReplyResult.stage("partial"));
                throw new IllegalStateException("boom");
            }
        });

        var response = runtime.roundTrip(transactionsJson(), WSHandleTransactionsResult.class);

        assertEquals(2, response.getResults().size());
        for (var result : response.getResults()) {
            assertEquals("boom", result.getError());
            assertNull(result.getStage());
        }
    }

    @Test
    void unknownTransactionHandlerReportsError() throws Exception {
        var response = runtime.roundTrip(transactionsJson(), WSHandleTransactionsResult.class);
        assertEquals("No transaction handler registered: txn-handler", response.getError());
    }

    @Test
    void eventProcessorDispatch() throws Exception {
        var received = new AtomicReference<List<ListenerEvent>>();
        runtime.registerEventProcessor("processor-1", new EventProcessor() {
            @Override
            public String name() { return "processor-1"; }

            @Override
            public void eventProcessorBatch(RequestContext reqContext,
                                            WSEventProcessorBatchResult result,
                                            io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchRequest batch) {
                received.set(batch.events());
                assertEquals("auth-1", reqContext.authRef());
            }
        });

        var json = """
                {"messageType":"event_processor_batch","id":"batch-2","handler":"processor-1",
                 "streamName":"stream-a","streamId":"stream-id-a","authRef":"auth-1",
                 "events":[{"idempotencyKey":"key-1","topic":"topic-1","data":{"value":42}}]}
                """;
        var response = runtime.roundTrip(json, WSEventProcessorBatchResult.class);

        assertNull(response.getError());
        assertEquals("batch-2", response.getId());
        assertEquals(1, received.get().size());
        assertEquals("key-1", received.get().get(0).idempotencyKey());
    }

    @Test
    void eventProcessorErrorPropagatedOnResult() throws Exception {
        runtime.registerEventProcessor("processor-1", new EventProcessor() {
            @Override
            public String name() { return "processor-1"; }

            @Override
            public void eventProcessorBatch(RequestContext reqContext,
                                            WSEventProcessorBatchResult result,
                                            io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchRequest batch) {
                throw new RuntimeException("processor exploded");
            }
        });

        var json = """
                {"messageType":"event_processor_batch","id":"batch-3","handler":"processor-1","events":[]}
                """;
        var response = runtime.roundTrip(json, WSEventProcessorBatchResult.class);
        assertEquals("processor exploded", response.getError());
    }

    @Test
    void eventSourceLifecycleDispatch() throws Exception {
        var source = new EventSource() {
            @Override
            public String name() { return "source-1"; }

            @Override
            public void eventSourcePoll(RequestContext reqContext, WSEventSourceConfig config,
                                        WSListenerPollResult result, WSListenerPollRequest request) {
                assertEquals("stream-id-b", config.streamId());
                assertEquals("{\"from\":\"here\"}", config.config().toString());
                result.setEvents(List.of(new ListenerEvent("evt-1", "topic-x",
                        JSON.MAPPER.createObjectNode().put("n", 1))));
                result.setCheckpoint(JSON.MAPPER.createObjectNode().put("offset", 10));
            }

            @Override
            public void eventSourceValidateConfig(RequestContext reqContext,
                                                  WSEventSourceValidateConfigResult result,
                                                  WSEventSourceValidateConfigRequest request) {
                result.setInitialCheckpoint(JSON.MAPPER.createObjectNode().put("offset", 0));
            }

            @Override
            public void eventSourceDelete(RequestContext reqContext,
                                          WSEventSourceDeleteResult result,
                                          WSEventSourceDeleteRequest request) {
            }
        };
        runtime.registerEventSource("source-1", source);

        // Config arrives first and is cached (no response expected)
        runtime.handleMessage("""
                {"messageType":"event_source_config","id":"cfg-1","handler":"source-1",
                 "streamName":"stream-b","streamId":"stream-id-b","config":{"from":"here"}}
                """);

        var pollResponse = runtime.roundTrip("""
                {"messageType":"event_source_poll","id":"poll-1","handler":"source-1",
                 "streamName":"stream-b","streamId":"stream-id-b"}
                """, WSListenerPollResult.class);
        assertNull(pollResponse.getError());
        assertEquals(1, pollResponse.getEvents().size());
        assertEquals(10, pollResponse.getCheckpoint().get("offset").asInt());

        var validateResponse = runtime.roundTrip("""
                {"messageType":"event_source_validate_config","id":"val-1","handler":"source-1",
                 "streamName":"stream-b","streamId":"stream-id-b","config":{}}
                """, WSEventSourceValidateConfigResult.class);
        assertEquals(0, validateResponse.getInitialCheckpoint().get("offset").asInt());

        var deleteResponse = runtime.roundTrip("""
                {"messageType":"event_source_delete","id":"del-1","handler":"source-1",
                 "streamName":"stream-b","streamId":"stream-id-b"}
                """, WSEventSourceDeleteResult.class);
        assertNull(deleteResponse.getError());

        // After delete, the cached config is gone — poll now errors
        var pollAfterDelete = runtime.roundTrip("""
                {"messageType":"event_source_poll","id":"poll-2","handler":"source-1",
                 "streamName":"stream-b","streamId":"stream-id-b"}
                """, WSListenerPollResult.class);
        assertNotNull(pollAfterDelete.getError());
    }

    @Test
    void setupTriggerAcksSuccessWithNoHandler() throws Exception {
        var response = runtime.roundTrip("""
                {"messageType":"setup-trigger-request","requestId":"trigger-1","authRef":"auth-xyz"}
                """, WSSetupTriggerResponse.class);
        assertEquals("trigger-1", response.requestId());
        assertEquals(WSSetupTriggerResponse.STATUS_SUCCESS, response.status());
        assertNull(response.errors());
    }

    @Test
    void setupTriggerRunsRegisteredHandler() throws Exception {
        var seenAuthRef = new AtomicReference<String>();
        runtime.registerSetupTriggerHandler(authRef -> {
            seenAuthRef.set(authRef);
            return List.of();
        });

        var response = runtime.roundTrip("""
                {"messageType":"setup-trigger-request","requestId":"trigger-2","authRef":"auth-xyz"}
                """, WSSetupTriggerResponse.class);
        assertEquals(WSSetupTriggerResponse.STATUS_SUCCESS, response.status());
        assertEquals("auth-xyz", seenAuthRef.get());
    }

    @Test
    void setupTriggerReportsHookErrors() throws Exception {
        runtime.registerSetupTriggerHandler(authRef -> List.of("hook-a: failed"));

        var response = runtime.roundTrip("""
                {"messageType":"setup-trigger-request","requestId":"trigger-3","authRef":""}
                """, WSSetupTriggerResponse.class);
        assertEquals(WSSetupTriggerResponse.STATUS_ERROR, response.status());
        assertEquals(List.of("hook-a: failed"), response.errors());
    }

    @Test
    void setupTriggerWithoutRequestIdIsDropped() throws Exception {
        runtime.handleMessage("""
                {"messageType":"setup-trigger-request","authRef":"auth-xyz"}
                """);
        assertNull(runtime.sentMessages.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS));
    }
}
