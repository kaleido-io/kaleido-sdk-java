// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolJsonTest {

    @Test
    void roundTripWSEnvelope() throws Exception {
        var envelope = new WSEnvelope(
                WSMessageType.HANDLE_TRANSACTIONS, "id-123", null,
                WSHandlerType.TRANSACTION_HANDLER, "my-handler",
                null, null, null);

        var json = JSON.MAPPER.writeValueAsString(envelope);
        assertTrue(json.contains("\"handle_transactions\""));
        assertTrue(json.contains("\"transaction_handler\""));
        assertFalse(json.contains("\"error\""));

        var parsed = JSON.MAPPER.readValue(json, WSEnvelope.class);
        assertEquals(WSMessageType.HANDLE_TRANSACTIONS, parsed.messageType());
        assertEquals("id-123", parsed.id());
        assertEquals("my-handler", parsed.handler());
    }

    @Test
    void registerProviderOmitsNullCapabilities() throws Exception {
        var meta = JSON.MAPPER.createObjectNode().put("version", "1.0");
        var message = WSRegisterProvider.of("id-1", "test-provider", meta, null);

        var json = JSON.MAPPER.writeValueAsString(message);
        assertTrue(json.contains("\"register_provider\""));
        assertFalse(json.contains("capabilities"));

        var withCapabilities = WSRegisterProvider.of("id-1", "test-provider", meta,
                new ProviderCapabilities(true));
        var jsonWithCapabilities = JSON.MAPPER.writeValueAsString(withCapabilities);
        assertTrue(jsonWithCapabilities.contains("\"hasSetupHooks\":true"));

        var parsed = JSON.MAPPER.readValue(jsonWithCapabilities, WSRegisterProvider.class);
        assertEquals(Boolean.TRUE, parsed.capabilities().hasSetupHooks());
    }

    @Test
    void roundTripHandleTransactionsResult() throws Exception {
        var result = new WSHandleTransactionsResult();
        result.setId("batch-1");
        result.setHandler("my-handler");
        var reply = new WSEvaluateReplyResult();
        reply.setStage("next-stage");
        reply.setStateUpdates(List.of(PatchOp.replace("/foo", (Object) "bar")));
        reply.setTriggers(List.of(new Trigger("my-topic", false)));
        result.setResults(List.of(reply));

        var json = JSON.MAPPER.writeValueAsString(result);
        assertTrue(json.contains("\"handle_transactions_result\""));
        assertTrue(json.contains("\"next-stage\""));
        assertTrue(json.contains("\"replace\""));

        var parsed = JSON.MAPPER.readValue(json, WSHandleTransactionsResult.class);
        assertEquals("batch-1", parsed.getId());
        assertEquals(PatchOpType.REPLACE, parsed.getResults().get(0).getStateUpdates().get(0).op());
    }

    @Test
    void parseHandleTransactionsWithDeadlineAndSequence() throws Exception {
        var json = """
                {"messageType":"handle_transactions","id":"b-1","deadline":"2026-07-14T00:00:00Z",
                 "handler":"h1","transactions":[
                   {"transactionId":"t1","workflowId":"w1","sequence":"000001","stage":"init",
                    "authRef":"auth-1","input":{"a":1},"configProfile":{"mode":"fast"},
                    "events":[{"topic":"t.a","data":{"x":1}}]}]}
                """;
        var parsed = JSON.MAPPER.readValue(json, WSHandleTransactions.class);
        assertEquals("2026-07-14T00:00:00Z", parsed.deadline());
        var txn = parsed.transactions().get(0);
        assertEquals("000001", txn.sequence());
        assertEquals("auth-1", txn.authRef());
        assertEquals("fast", txn.configProfile().get("mode").asText());
        assertEquals("t.a", txn.events().get(0).topic());
    }

    @Test
    void roundTripSetupTrigger() throws Exception {
        var request = JSON.MAPPER.readValue("""
                {"messageType":"setup-trigger-request","requestId":"r-1","authRef":"a-1"}
                """, WSSetupTriggerRequest.class);
        assertEquals(WSMessageType.SETUP_TRIGGER_REQUEST, request.messageType());
        assertEquals("r-1", request.requestId());
        assertEquals("a-1", request.authRef());

        var success = JSON.MAPPER.writeValueAsString(WSSetupTriggerResponse.success("r-1"));
        assertTrue(success.contains("\"setup-trigger-response\""));
        assertTrue(success.contains("\"success\""));
        assertFalse(success.contains("errors"));

        var error = JSON.MAPPER.writeValueAsString(
                WSSetupTriggerResponse.error("r-1", List.of("hook: bad")));
        assertTrue(error.contains("\"error\""));
        assertTrue(error.contains("hook: bad"));
    }

    @Test
    void roundTripEngineAPISubmitTransactions() throws Exception {
        var txn = new AsyncTransactionInput(
                "idem-1", "wf-1", null, "op-1",
                JSON.MAPPER.createObjectNode().put("key", "val"),
                Map.of("env", "test"));

        var message = WSEngineAPISubmitTransactions.of("req-1", "active-1", "auth-ref-1", List.of(txn));

        var json = JSON.MAPPER.writeValueAsString(message);
        assertTrue(json.contains("\"engineapi_submit_transactions\""));

        var parsed = JSON.MAPPER.readValue(json, WSEngineAPISubmitTransactions.class);
        assertEquals("req-1", parsed.id());
        assertEquals("active-1", parsed.activeRequestId());
        assertEquals("idem-1", parsed.transactions().get(0).idempotencyKey());
    }

    @Test
    void listenerPollResultSerializesMutatedFields() throws Exception {
        var result = new WSListenerPollResult();
        result.setId("poll-1");
        result.setHandler("source-1");
        result.setCheckpoint(JSON.MAPPER.createObjectNode().put("dealt", 5));
        result.setEvents(List.of(new ListenerEvent("k-1", "topic-a",
                JSON.MAPPER.createObjectNode().put("card", "ace"))));

        var json = JSON.MAPPER.writeValueAsString(result);
        assertTrue(json.contains("\"event_source_poll_result\""));
        assertTrue(json.contains("\"dealt\":5"));
        assertTrue(json.contains("\"card\":\"ace\""));
    }

    @Test
    void deserializeUnknownFieldsIgnored() throws Exception {
        var json = """
                {"messageType":"register_provider","id":"x","providerName":"p","unknownField":123}
                """;
        var parsed = JSON.MAPPER.readValue(json, WSRegisterProvider.class);
        assertEquals("p", parsed.providerName());
    }

    @Test
    void messageTypeWireValues() throws Exception {
        // These string values are the wire contract shared with the platform and other SDKs
        assertEquals("\"service-proxy-request\"", JSON.MAPPER.writeValueAsString(WSMessageType.SERVICE_PROXY_REQUEST));
        assertEquals("\"service-proxy-response\"", JSON.MAPPER.writeValueAsString(WSMessageType.SERVICE_PROXY_RESPONSE));
        assertEquals("\"setup-trigger-request\"", JSON.MAPPER.writeValueAsString(WSMessageType.SETUP_TRIGGER_REQUEST));
        assertEquals("\"setup-trigger-response\"", JSON.MAPPER.writeValueAsString(WSMessageType.SETUP_TRIGGER_RESPONSE));
        assertEquals("\"engineapi_submit_transactions\"", JSON.MAPPER.writeValueAsString(WSMessageType.ENGINE_API_SUBMIT_TRANSACTIONS));
    }
}
