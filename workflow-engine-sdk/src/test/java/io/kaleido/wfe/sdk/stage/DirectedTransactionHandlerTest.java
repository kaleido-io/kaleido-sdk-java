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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DirectedTransactionHandlerTest {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestInput(
            @JsonProperty("stageDirector") StageDirector stageDirector,
            String value
    ) implements WithStageDirector {
        @Override
        public StageDirector getStageDirector() {
            return stageDirector;
        }
    }

    private static WSHandleTransactions buildRequest(String action, String nextStage, String value) {
        var input = JSON.MAPPER.createObjectNode();
        input.putObject("stageDirector")
                .put("action", action)
                .put("outputPath", "/result")
                .put("nextStage", nextStage)
                .put("failureStage", "failed");
        input.put("value", value);

        return new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-1",
                WSHandlerType.TRANSACTION_HANDLER, "test-directed",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-1", "wf-1", "op-1", 0L, "idem-1", 0,
                        null, null, "stage-1", null, null,
                        null, null, input, null, null, null)));
    }

    private static WSHandleTransactions buildBatchRequest(String action, List<String> values) {
        var transactions = values.stream().map(v -> {
            var input = JSON.MAPPER.createObjectNode();
            input.putObject("stageDirector")
                    .put("action", action)
                    .put("outputPath", "/result")
                    .put("nextStage", "done")
                    .put("failureStage", "failed");
            input.put("value", v);
            return new WSHandleTransaction(
                    "txn-" + v, "wf-1", "op-1", 0L, "idem-" + v, 0,
                    null, null, "stage-1", null, null,
                    null, null, input, null, null, null);
        }).toList();

        return new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-batch",
                WSHandlerType.TRANSACTION_HANDLER, "test-directed",
                null, null, transactions);
    }

    @Test
    void dispatchesByAction() throws Exception {
        var handler = new DirectedTransactionHandler<TestInput>(
                "test-directed", TestInput.class, Map.of(
                "doA", DirectedActionConfig.parallel(input -> EvalResult.complete()),
                "doB", DirectedActionConfig.parallel(input -> EvalResult.waiting())
        )) {};

        var resultA = handler.handleTransactionBatch(buildRequest("doA", "next", "x"));
        assertEquals("next", resultA.results().get(0).stage());

        var resultB = handler.handleTransactionBatch(buildRequest("doB", "next", "y"));
        assertNull(resultB.results().get(0).stage());
    }

    @Test
    void unknownActionReturnsError() throws Exception {
        var handler = new DirectedTransactionHandler<TestInput>(
                "test-directed", TestInput.class, Map.of(
                "known", DirectedActionConfig.parallel(input -> EvalResult.complete())
        )) {};

        var result = handler.handleTransactionBatch(buildRequest("unknown", "next", "x"));
        assertNotNull(result.results().get(0).error());
        assertTrue(result.results().get(0).error().contains("Unknown action"));
    }

    @Test
    void parallelModeDispatchesConcurrently() throws Exception {
        var callCount = new AtomicInteger(0);
        var handler = new DirectedTransactionHandler<TestInput>(
                "test-directed", TestInput.class, Map.of(
                "work", DirectedActionConfig.parallel(input -> {
                    callCount.incrementAndGet();
                    return EvalResult.complete();
                })
        )) {};

        var result = handler.handleTransactionBatch(
                buildBatchRequest("work", List.of("a", "b", "c")));

        assertEquals(3, result.results().size());
        assertEquals(3, callCount.get());
        for (var r : result.results()) {
            assertEquals("done", r.stage());
            assertNull(r.error());
        }
    }

    @Test
    void batchModePassesAllToHandler() throws Exception {
        var handler = new DirectedTransactionHandler<TestInput>(
                "test-directed", TestInput.class, Map.of(
                "batch-op", DirectedActionConfig.batch(inputs -> {
                    assertEquals(3, inputs.size());
                    return inputs.stream()
                            .map(i -> EvalResult.complete())
                            .toList();
                })
        )) {};

        var result = handler.handleTransactionBatch(
                buildBatchRequest("batch-op", List.of("x", "y", "z")));

        assertEquals(3, result.results().size());
        for (var r : result.results()) {
            assertEquals("done", r.stage());
        }
    }

    @Test
    void handlerExceptionClassifiedViaErrorMap() throws Exception {
        var handler = new DirectedTransactionHandler<TestInput>(
                "test-directed", TestInput.class, Map.of(
                "risky", DirectedActionConfig.parallel(input -> {
                    throw new RuntimeException("insufficient funds for transfer");
                })
        )) {};

        var input = JSON.MAPPER.createObjectNode();
        var sd = input.putObject("stageDirector");
        sd.put("action", "risky");
        sd.put("outputPath", "/result");
        sd.put("nextStage", "done");
        sd.put("failureStage", "failed");
        var errorMapNode = sd.putArray("errorMap");
        var entry = errorMapNode.addObject();
        entry.put("pattern", "insufficient funds");
        entry.put("type", "HARD_FAILURE");
        input.put("value", "test");

        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-1",
                WSHandlerType.TRANSACTION_HANDLER, "test-directed",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-1", "wf-1", "op-1", 0L, "idem-1", 0,
                        null, null, "stage-1", null, null,
                        null, null, input, null, null, null)));

        var result = handler.handleTransactionBatch(request);
        assertEquals("failed", result.results().get(0).stage());
        assertNull(result.results().get(0).error());
    }

    @Test
    void handlerExceptionWithTransientErrorMap() throws Exception {
        var handler = new DirectedTransactionHandler<TestInput>(
                "test-directed", TestInput.class, Map.of(
                "flaky", DirectedActionConfig.parallel(input -> {
                    throw new RuntimeException("contract reverted: out of gas");
                })
        )) {};

        var input = JSON.MAPPER.createObjectNode();
        var sd = input.putObject("stageDirector");
        sd.put("action", "flaky");
        sd.put("outputPath", "/result");
        sd.put("nextStage", "done");
        sd.put("failureStage", "failed");
        var errorMapNode = sd.putArray("errorMap");
        var entry = errorMapNode.addObject();
        entry.put("pattern", "contract reverted");
        entry.put("type", "TRANSIENT_ERROR");
        input.put("value", "test");

        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-1",
                WSHandlerType.TRANSACTION_HANDLER, "test-directed",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-1", "wf-1", "op-1", 0L, "idem-1", 0,
                        null, null, "stage-1", null, null,
                        null, null, input, null, null, null)));

        var result = handler.handleTransactionBatch(request);
        assertNull(result.results().get(0).stage());
        assertNotNull(result.results().get(0).error());
        assertTrue(result.results().get(0).error().contains("contract reverted"));
    }

    @Test
    void unmatchedExceptionReturnsSdkError() throws Exception {
        var handler = new DirectedTransactionHandler<TestInput>(
                "test-directed", TestInput.class, Map.of(
                "boom", DirectedActionConfig.parallel(input -> {
                    throw new RuntimeException("unexpected failure");
                })
        )) {};

        var result = handler.handleTransactionBatch(buildRequest("boom", "done", "x"));
        assertNotNull(result.results().get(0).error());
        assertTrue(result.results().get(0).error().contains("unexpected failure"));
    }
}
