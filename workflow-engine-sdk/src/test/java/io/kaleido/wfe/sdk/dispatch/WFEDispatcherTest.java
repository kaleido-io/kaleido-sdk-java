// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.dispatch;

import io.kaleido.wfe.sdk.handlers.EventProcessor;
import io.kaleido.wfe.sdk.handlers.EventSource;
import io.kaleido.wfe.sdk.handlers.Handler;
import io.kaleido.wfe.sdk.handlers.TransactionHandler;
import io.kaleido.wfe.sdk.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WFEDispatcherTest {

    @Test
    void dispatchesToTransactionHandler() throws Exception {
        var handler = new TestTransactionHandler();
        var dispatcher = new WFEDispatcher(
                Map.of("test-handler", handler), new AtomicReference<>());

        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-1",
                WSHandlerType.TRANSACTION_HANDLER, "test-handler",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-1", "wf-1", "op-1", 0L, "idem-1", 0,
                        null, null, "init", null, null,
                        null, null,
                        JSON.MAPPER.createObjectNode().put("hello", "world"),
                        null, null, null)));

        var json = JSON.MAPPER.writeValueAsString(request);
        var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, json, sent::add);

        assertEquals(1, sent.size());
        var result = (WSHandleTransactionsResult) sent.get(0);
        assertEquals("req-1", result.id());
        assertEquals(1, result.results().size());
        assertEquals("done", result.results().get(0).stage());
    }

    @Test
    void dispatchesToEventProcessor() throws Exception {
        var processor = new TestEventProcessor();
        var dispatcher = new WFEDispatcher(
                Map.of("test-ep", processor), new AtomicReference<>());

        var request = new WSEventProcessorBatchRequest(
                WSMessageType.EVENT_PROCESSOR_BATCH, "evt-1",
                WSHandlerType.EVENT_PROCESSOR, "test-ep",
                null, null,
                "stream-1", "stream-id-1",
                List.of(new ListenerEvent("idem-1", "topic-1",
                        JSON.MAPPER.createObjectNode())),
                null);

        var json = JSON.MAPPER.writeValueAsString(request);
        var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, json, sent::add);

        assertEquals(1, sent.size());
        var result = (WSEventProcessorBatchResult) sent.get(0);
        assertEquals("evt-1", result.id());
        assertNull(result.error());
    }

    @Test
    void unknownHandlerReturnsError() throws Exception {
        var dispatcher = new WFEDispatcher(Map.of(), new AtomicReference<>());

        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-2",
                WSHandlerType.TRANSACTION_HANDLER, "missing-handler",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-2", "wf-1", "op-1", 0L, "idem-2", 0,
                        null, null, "init", null, null,
                        null, null, null, null, null, null)));

        var json = JSON.MAPPER.writeValueAsString(request);
        var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, json, sent::add);

        assertEquals(1, sent.size());
        var result = (WSHandleTransactionsResult) sent.get(0);
        assertNotNull(result.error());
        assertTrue(result.error().contains("Handler not found"));
    }

    @Test
    void nullMessageTypeIsIgnored() {
        var dispatcher = new WFEDispatcher(Map.of(), new AtomicReference<>());
        var envelope = new WSEnvelope(null, "id-1", null, null, null, null);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, "{}", sent::add);

        assertTrue(sent.isEmpty());
    }

    @Test
    void panicSafeResponseOnHandlerException() throws Exception {
        Handler handler = new TransactionHandler() {
            @Override
            public String name() { return "boom"; }

            @Override
            public WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) {
                throw new RuntimeException("kaboom");
            }
        };
        var dispatcher = new WFEDispatcher(Map.of("boom", handler), new AtomicReference<>());

        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-boom",
                WSHandlerType.TRANSACTION_HANDLER, "boom",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-1", "wf-1", "op-1", 0L, "idem-1", 0,
                        null, null, "init", null, null,
                        null, null, null, null, null, null)));

        var json = JSON.MAPPER.writeValueAsString(request);
        var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, json, sent::add);

        assertEquals(1, sent.size());
        var result = (WSHandleTransactionsResult) sent.get(0);
        assertEquals("req-boom", result.id());
        assertNotNull(result.error());
        assertTrue(result.error().contains("kaboom"));
    }

    @Test
    void rejectsMismatchedBatchResults() throws Exception {
        // Handler returns 1 result for a 2-transaction batch -- dispatcher must
        // surface an error response rather than silently passing it through.
        Handler handler = new TransactionHandler() {
            @Override
            public String name() { return "shrink"; }

            @Override
            public WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) {
                return WSHandleTransactionsResult.forRequest(request,
                        List.of(WSHandleTransactionResult.stage("done")));
            }
        };
        var dispatcher = new WFEDispatcher(Map.of("shrink", handler), new AtomicReference<>());

        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-shrink",
                WSHandlerType.TRANSACTION_HANDLER, "shrink",
                null, null,
                List.of(
                        new WSHandleTransaction("txn-1", "wf-1", "op-1", 0L, "idem-1", 0,
                                null, null, "init", null, null, null, null, null, null, null, null),
                        new WSHandleTransaction("txn-2", "wf-1", "op-2", 0L, "idem-2", 0,
                                null, null, "init", null, null, null, null, null, null, null, null)));

        var json = JSON.MAPPER.writeValueAsString(request);
        var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, json, sent::add);

        assertEquals(1, sent.size());
        var result = (WSHandleTransactionsResult) sent.get(0);
        assertNotNull(result.error());
        assertTrue(result.error().contains("one result per input"));
    }

    @Test
    void dispatchesToEventSource() throws Exception {
        var source = new TestEventSource();
        var dispatcher = new WFEDispatcher(
                Map.of("test-es", source), new AtomicReference<>());

        var poll = new WSEventSourcePoll(
                WSMessageType.EVENT_SOURCE_POLL, "poll-1",
                WSHandlerType.EVENT_SOURCE, "test-es",
                null, null, "stream-1", "stream-id-1", "ck-0", 10, null);

        var json = JSON.MAPPER.writeValueAsString(poll);
        var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, json, sent::add);

        assertEquals(1, sent.size());
        var result = (WSEventSourcePollResult) sent.get(0);
        assertEquals("poll-1", result.id());
        assertEquals("ck-1", result.checkpoint());
    }

    @Test
    void eventSourceConfigInvokesOnConfigChangedSynchronously() throws Exception {
        var source = new TestEventSource();
        var dispatcher = new WFEDispatcher(
                Map.of("test-es", source), new AtomicReference<>());

        var config = new WSEventSourceConfig(
                WSMessageType.EVENT_SOURCE_CONFIG, "cfg-1",
                WSHandlerType.EVENT_SOURCE, "test-es",
                null, null, "stream-1", "stream-id-1",
                JSON.MAPPER.createObjectNode().put("foo", "bar"));

        var json = JSON.MAPPER.writeValueAsString(config);
        var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, json, sent::add);

        // No response is emitted for EVENT_SOURCE_CONFIG (Go SDK invariant)
        assertTrue(sent.isEmpty());
        assertEquals("stream-id-1", source.lastConfigChangedStreamId);
    }

    private static class TestTransactionHandler implements TransactionHandler {
        @Override
        public String name() { return "test-handler"; }

        @Override
        public WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) {
            var results = request.transactions().stream()
                    .map(txn -> WSHandleTransactionResult.stage("done"))
                    .toList();
            return WSHandleTransactionsResult.forRequest(request, results);
        }
    }

    private static class TestEventProcessor implements EventProcessor {
        @Override
        public String name() { return "test-ep"; }

        @Override
        public WSEventProcessorBatchResult processEvents(WSEventProcessorBatchRequest request) {
            return WSEventProcessorBatchResult.forRequest(request);
        }
    }

    private static class TestEventSource implements EventSource {
        volatile String lastConfigChangedStreamId;
        volatile WSEventSourceConfig lastConfigRequest;

        @Override
        public String name() { return "test-es"; }

        @Override
        public WSEventSourceValidateConfigResult validateConfig(WSEventSourceValidateConfig request) {
            return WSEventSourceValidateConfigResult.forRequest(request, request.config());
        }

        @Override
        public WSEventSourcePollResult poll(WSEventSourcePoll request) {
            return WSEventSourcePollResult.forRequest(request, "ck-1", List.of());
        }

        @Override
        public WSEventSourceDeleteResult delete(WSEventSourceDelete request) {
            return WSEventSourceDeleteResult.forRequest(request);
        }

        @Override
        public void onConfigChanged(WSEventSourceConfig request) {
            this.lastConfigChangedStreamId = request.streamId();
            this.lastConfigRequest = request;
        }
    }
}
