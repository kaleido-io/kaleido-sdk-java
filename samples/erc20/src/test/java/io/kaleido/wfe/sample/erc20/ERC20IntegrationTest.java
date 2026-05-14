// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sample.erc20;

import io.kaleido.wfe.sdk.protocol.*;
import io.kaleido.wfe.sdk.stage.StageDirector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests both stages of the ERC-20 transfer handler using synthetic inputs
 * (no live WS engine or blockchain required).
 */
class ERC20IntegrationTest {

    @Test
    void submitStageCompletesSuccessfully() throws Exception {
        var handler = new ERC20TransactionHandler();

        var input = JSON.MAPPER.createObjectNode();
        input.putObject("stageDirector")
                .put("action", "submit")
                .put("outputPath", "/state/submitResult")
                .put("nextStage", "confirm")
                .put("failureStage", "failed");
        input.put("contractAddress", "0x1234567890abcdef");
        input.put("to", "0xrecipient");
        input.put("amount", "1000000");

        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-1",
                WSHandlerType.TRANSACTION_HANDLER, "erc20-transfer",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-1", "wf-1", "transfer", 0L, "idem-1", 0,
                        null, null, "submit", null, null,
                        "auth-ref-1", null, input,
                        null, null, null)));

        var result = handler.handleTransactionBatch(request);

        assertEquals(WSMessageType.HANDLE_TRANSACTIONS_RESULT, result.messageType());
        assertEquals(1, result.results().size());
        assertEquals("confirm", result.results().get(0).stage());
        assertNull(result.results().get(0).error());
    }

    @Test
    void confirmStageCompletesSuccessfully() throws Exception {
        var handler = new ERC20TransactionHandler();

        var input = JSON.MAPPER.createObjectNode();
        input.putObject("stageDirector")
                .put("action", "confirm")
                .put("outputPath", "/state/confirmResult")
                .put("nextStage", "done")
                .put("failureStage", "failed");
        input.put("contractAddress", "0x1234567890abcdef");
        input.put("to", "0xrecipient");
        input.put("amount", "1000000");

        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-2",
                WSHandlerType.TRANSACTION_HANDLER, "erc20-transfer",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-2", "wf-1", "transfer", 1L, "idem-2", 0,
                        null, null, "confirm", null, null,
                        "auth-ref-1", null, input,
                        null, null, null)));

        var result = handler.handleTransactionBatch(request);

        assertEquals(WSMessageType.HANDLE_TRANSACTIONS_RESULT, result.messageType());
        assertEquals(1, result.results().size());
        assertEquals("done", result.results().get(0).stage());
        assertNull(result.results().get(0).error());
    }

    @Test
    void unknownActionReturnsError() throws Exception {
        var handler = new ERC20TransactionHandler();

        var input = JSON.MAPPER.createObjectNode();
        input.putObject("stageDirector")
                .put("action", "nonexistent")
                .put("outputPath", "/state/result")
                .put("nextStage", "done");
        input.put("contractAddress", "0x1234");
        input.put("to", "0x5678");
        input.put("amount", "100");

        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-3",
                WSHandlerType.TRANSACTION_HANDLER, "erc20-transfer",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-3", "wf-1", "transfer", 0L, "idem-3", 0,
                        null, null, "bad", null, null,
                        null, null, input,
                        null, null, null)));

        var result = handler.handleTransactionBatch(request);

        assertEquals(1, result.results().size());
        assertNotNull(result.results().get(0).error());
        assertTrue(result.results().get(0).error().contains("Unknown action"));
    }
}
