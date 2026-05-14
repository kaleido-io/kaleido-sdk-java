// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sample;

import io.kaleido.wfe.sdk.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that drives the hello-world handlers directly against
 * a mock transaction/event batch, without requiring a live WS engine.
 */
class HelloWorldIntegrationTest {

    @Test
    void helloTransactionHandler() throws Exception {
        var handler = new HelloTransactionHandler();
        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-1",
                WSHandlerType.TRANSACTION_HANDLER, "hello",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-1", "wf-1", "op-1", 0L, "idem-1", 0,
                        null, null, "init", null, null,
                        null, null,
                        JSON.MAPPER.createObjectNode().put("greeting", "world"),
                        null, null, null)));

        var result = handler.handleTransactionBatch(request);

        assertEquals(WSMessageType.HANDLE_TRANSACTIONS_RESULT, result.messageType());
        assertEquals("req-1", result.id());
        assertNotNull(result.results());
        assertEquals(1, result.results().size());
        assertEquals("complete", result.results().get(0).stage());
        assertNull(result.results().get(0).error());
    }

    @Test
    void echoEventProcessor() throws Exception {
        var processor = new EchoEventProcessor();
        var request = new WSEventProcessorBatchRequest(
                WSMessageType.EVENT_PROCESSOR_BATCH, "evt-1",
                WSHandlerType.EVENT_PROCESSOR, "echo",
                null, null,
                "test-stream", "stream-id-1",
                List.of(new ListenerEvent(
                        "idem-1", "topic-1",
                        JSON.MAPPER.createObjectNode().put("key", "value"))),
                "auth-ref-1");

        var result = processor.processEvents(request);

        assertEquals(WSMessageType.EVENT_PROCESSOR_BATCH_RESULT, result.messageType());
        assertEquals("evt-1", result.id());
        assertNull(result.error());
    }
}
