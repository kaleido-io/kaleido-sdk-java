// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.stage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kaleido.wfe.sdk.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorMapTest {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorTestInput(
            @JsonProperty("stageDirector") StageDirector stageDirector,
            String data
    ) implements WithStageDirector {
        @Override
        public StageDirector getStageDirector() {
            return stageDirector;
        }
    }

    private DirectedTransactionHandler<ErrorTestInput> handlerThatThrows(String errorMessage) {
        return new DirectedTransactionHandler<ErrorTestInput>(
                "error-test", ErrorTestInput.class, Map.of(
                "action", DirectedActionConfig.parallel(input -> {
                    throw new RuntimeException(errorMessage);
                })
        )) {};
    }

    private WSHandleTransactions requestWithErrorMap(List<ErrorMapEntry> entries) {
        var input = JSON.MAPPER.createObjectNode();
        var sd = input.putObject("stageDirector");
        sd.put("action", "action");
        sd.put("outputPath", "/out");
        sd.put("nextStage", "done");
        sd.put("failureStage", "failed");
        var arr = sd.putArray("errorMap");
        for (var e : entries) {
            var node = arr.addObject();
            node.put("pattern", e.pattern());
            node.put("type", e.type().name());
        }
        input.put("data", "test");

        return new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-1",
                WSHandlerType.TRANSACTION_HANDLER, "error-test",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-1", "wf-1", "op", 0L, "idem-1", 0,
                        null, null, "s1", null, null,
                        null, null, input, null, null, null)));
    }

    @Test
    void hardFailurePatternMatchTransitionsToFailureStage() throws Exception {
        var handler = handlerThatThrows("insufficient funds for transfer");
        var request = requestWithErrorMap(List.of(
                new ErrorMapEntry("insufficient funds", EvalResultType.HARD_FAILURE)
        ));

        var result = handler.handleTransactionBatch(request);
        assertEquals("failed", result.results().get(0).stage());
        assertNull(result.results().get(0).error());
    }

    @Test
    void transientErrorPatternMatchReturnsRetry() throws Exception {
        var handler = handlerThatThrows("contract reverted: out of gas");
        var request = requestWithErrorMap(List.of(
                new ErrorMapEntry("contract reverted", EvalResultType.TRANSIENT_ERROR)
        ));

        var result = handler.handleTransactionBatch(request);
        assertNull(result.results().get(0).stage());
        assertNotNull(result.results().get(0).error());
        assertTrue(result.results().get(0).error().contains("contract reverted"));
    }

    @Test
    void fixableErrorPatternMatch() throws Exception {
        var handler = handlerThatThrows("nonce too low");
        var request = requestWithErrorMap(List.of(
                new ErrorMapEntry("nonce too low", EvalResultType.FIXABLE_ERROR)
        ));

        var result = handler.handleTransactionBatch(request);
        assertNull(result.results().get(0).stage());
        assertNotNull(result.results().get(0).error());
        assertTrue(result.results().get(0).error().contains("nonce too low"));
    }

    @Test
    void noMatchFallsThroughToGenericError() throws Exception {
        var handler = handlerThatThrows("something completely unexpected");
        var request = requestWithErrorMap(List.of(
                new ErrorMapEntry("insufficient funds", EvalResultType.HARD_FAILURE),
                new ErrorMapEntry("contract reverted", EvalResultType.TRANSIENT_ERROR)
        ));

        var result = handler.handleTransactionBatch(request);
        assertNotNull(result.results().get(0).error());
        assertTrue(result.results().get(0).error().contains("something completely unexpected"));
    }

    @Test
    void firstMatchWins() throws Exception {
        var handler = handlerThatThrows("insufficient funds and contract reverted");
        var request = requestWithErrorMap(List.of(
                new ErrorMapEntry("insufficient funds", EvalResultType.HARD_FAILURE),
                new ErrorMapEntry("contract reverted", EvalResultType.TRANSIENT_ERROR)
        ));

        var result = handler.handleTransactionBatch(request);
        // First pattern matches -> HARD_FAILURE -> transitions to failure stage
        assertEquals("failed", result.results().get(0).stage());
    }

    @Test
    void regexPatternsWork() throws Exception {
        var handler = handlerThatThrows("error code: 429 rate limited");
        var request = requestWithErrorMap(List.of(
                new ErrorMapEntry("code: \\d+ rate", EvalResultType.TRANSIENT_ERROR)
        ));

        var result = handler.handleTransactionBatch(request);
        assertNull(result.results().get(0).stage());
        assertNotNull(result.results().get(0).error());
    }

    @Test
    void emptyErrorMapFallsThrough() throws Exception {
        var handler = handlerThatThrows("some error");
        var request = requestWithErrorMap(List.of());

        var result = handler.handleTransactionBatch(request);
        assertNotNull(result.results().get(0).error());
        assertTrue(result.results().get(0).error().contains("some error"));
    }
}
