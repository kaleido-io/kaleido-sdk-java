// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolJsonTest {

    @Test
    void roundTripWSEnvelope() throws Exception {
        var envelope = new WSEnvelope(
                WSMessageType.HANDLE_TRANSACTIONS, "id-123",
                WSHandlerType.TRANSACTION_HANDLER, "my-handler",
                null, null);

        var json = JSON.MAPPER.writeValueAsString(envelope);
        assertTrue(json.contains("\"handle_transactions\""));
        assertTrue(json.contains("\"transaction_handler\""));
        assertTrue(json.contains("\"my-handler\""));
        assertFalse(json.contains("\"error\""));

        var parsed = JSON.MAPPER.readValue(json, WSEnvelope.class);
        assertEquals(WSMessageType.HANDLE_TRANSACTIONS, parsed.messageType());
        assertEquals("id-123", parsed.id());
        assertEquals("my-handler", parsed.handler());
    }

    @Test
    void roundTripWSRegisterProvider() throws Exception {
        var meta = JSON.MAPPER.createObjectNode().put("version", "1.0");
        var msg = WSRegisterProvider.of("test-provider", meta);

        var json = JSON.MAPPER.writeValueAsString(msg);
        assertTrue(json.contains("\"register_provider\""));
        assertTrue(json.contains("\"test-provider\""));

        var parsed = JSON.MAPPER.readValue(json, WSRegisterProvider.class);
        assertEquals("test-provider", parsed.providerName());
        assertEquals(WSMessageType.REGISTER_PROVIDER, parsed.messageType());
    }

    @Test
    void roundTripWSHandleTransactionsResult() throws Exception {
        var result = new WSHandleTransactionResult(
                "next-stage", null, null,
                List.of(PatchOp.replace("/foo", JSON.MAPPER.valueToTree("bar"))),
                List.of(new Trigger("my-topic", false)),
                null, null);

        var json = JSON.MAPPER.writeValueAsString(result);
        assertTrue(json.contains("\"next-stage\""));
        assertTrue(json.contains("\"replace\""));
        assertTrue(json.contains("\"my-topic\""));

        var parsed = JSON.MAPPER.readValue(json, WSHandleTransactionResult.class);
        assertEquals("next-stage", parsed.stage());
        assertEquals(1, parsed.stateUpdates().size());
        assertEquals(PatchOpType.REPLACE, parsed.stateUpdates().get(0).op());
    }

    @Test
    void roundTripEngineAPISubmitTransactions() throws Exception {
        var txn = new AsyncTransactionInput(
                "idem-1", "wf-1", null, "op-1",
                JSON.MAPPER.createObjectNode().put("key", "val"),
                Map.of("env", "test"));

        var msg = WSEngineAPISubmitTransactions.of("req-1", "active-1", "auth-ref-1", List.of(txn));

        var json = JSON.MAPPER.writeValueAsString(msg);
        assertTrue(json.contains("\"engineapi_submit_transactions\""));
        assertTrue(json.contains("\"idem-1\""));

        var parsed = JSON.MAPPER.readValue(json, WSEngineAPISubmitTransactions.class);
        assertEquals("req-1", parsed.id());
        assertEquals("active-1", parsed.activeRequestId());
        assertEquals(1, parsed.transactions().size());
        assertEquals("idem-1", parsed.transactions().get(0).idempotencyKey());
    }

    @Test
    void roundTripEventProcessorBatch() throws Exception {
        var event = new ListenerEvent("key-1", "topic-1",
                JSON.MAPPER.createObjectNode().put("data", "value"));
        var request = new WSEventProcessorBatchRequest(
                WSMessageType.EVENT_PROCESSOR_BATCH, "batch-1",
                WSHandlerType.EVENT_PROCESSOR, "my-ep",
                null, null, "stream-1", "stream-id-1",
                List.of(event), "auth-1");

        var json = JSON.MAPPER.writeValueAsString(request);
        assertTrue(json.contains("\"event_processor_batch\""));
        assertTrue(json.contains("\"stream-1\""));

        var parsed = JSON.MAPPER.readValue(json, WSEventProcessorBatchRequest.class);
        assertEquals("batch-1", parsed.id());
        assertEquals(1, parsed.events().size());
    }

    @Test
    void deserializeUnknownFieldsIgnored() throws Exception {
        var json = """
                {"messageType":"register_provider","id":"x","providerName":"p","unknownField":123}
                """;
        var parsed = JSON.MAPPER.readValue(json, WSRegisterProvider.class);
        assertEquals("p", parsed.providerName());
    }
}
