// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.stage;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.PatchOp;
import io.kaleido.workflowengine.sdk.protocol.PatchOpType;
import io.kaleido.workflowengine.sdk.protocol.Trigger;
import io.kaleido.workflowengine.sdk.protocol.WSEvaluateTransaction;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;
import io.kaleido.workflowengine.sdk.protocol.WSMessageType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StageDirectorHelperTest {

    private static final StageDirector DIRECTOR =
            new StageDirector("deploy", "/output", "verify", null, "failed");

    private static WSEvaluateTransaction transaction(JsonNode input) {
        return new WSEvaluateTransaction("handler-1", "wf-1", "txn-1", "1", "idem-1",
                null, null, null, "init", null, null, null, input, null, null);
    }

    private static JsonNode json(String value) throws Exception {
        return JSON.MAPPER.readTree(value);
    }

    // ── mapOutput ───────────────────────────────────────────────────────────

    @Test
    void completeTransitionsToNextStage() throws Exception {
        var result = StageDirectorHelper.mapOutput(DIRECTOR, transaction(null),
                ActionResult.complete().withOutput(json("{\"txHash\":\"0xabc\"}")));

        assertEquals("verify", result.getStage());
        assertNull(result.getError());
        assertEquals(1, result.getStateUpdates().size());
        assertEquals(PatchOpType.ADD, result.getStateUpdates().get(0).op());
        assertEquals("/output", result.getStateUpdates().get(0).path());
    }

    @Test
    void completeWithCustomStageOverridesNextStage() {
        var result = StageDirectorHelper.mapOutput(DIRECTOR, transaction(null),
                ActionResult.complete().withCustomStage("elsewhere"));
        assertEquals("elsewhere", result.getStage());
    }

    @Test
    void completeTransitionsToSubflowWhenNextStageAbsent() {
        var director = new StageDirector("deploy", "/output", null, "resolveMint", "failed");
        var result = StageDirectorHelper.mapOutput(director, transaction(null), ActionResult.complete());
        assertEquals("resolveMint", result.getSubflow());
        assertNull(result.getStage());
        assertNull(result.getError());
    }

    @Test
    void completeWithSubflowOutputOverridesDirectorNextSubflow() {
        var director = new StageDirector("deploy", "/output", null, "resolveMint", "failed");
        var result = StageDirectorHelper.mapOutput(director, transaction(null),
                ActionResult.complete().withSubflow("otherSubflow"));
        assertEquals("otherSubflow", result.getSubflow());
        assertNull(result.getStage());
    }

    @Test
    void completePrefersNextStageOverNextSubflowWhenBothSet() {
        var director = new StageDirector("deploy", "/output", "verify", "resolveMint", "failed");
        var result = StageDirectorHelper.mapOutput(director, transaction(null), ActionResult.complete());
        assertEquals("verify", result.getStage());
        assertNull(result.getSubflow());
    }

    @Test
    void completeWithoutNextStageErrors() {
        var director = new StageDirector("deploy", "/output", null, null, "failed");
        var result = StageDirectorHelper.mapOutput(director, transaction(null), ActionResult.complete());
        assertNull(result.getStage());
        assertTrue(result.getError().contains("KA140604"));
    }

    @Test
    void waitingKeepsStageAndCarriesDeadline() {
        var result = StageDirectorHelper.mapOutput(DIRECTOR, transaction(null),
                ActionResult.waiting().withDeadline("2026-12-01T00:00:00Z"));
        assertNull(result.getStage());
        assertNull(result.getError());
        assertEquals("2026-12-01T00:00:00Z", result.getDeadline());
    }

    @Test
    void deadlineOnNonWaitingBecomesFixableError() {
        var result = StageDirectorHelper.mapOutput(DIRECTOR, transaction(null),
                ActionResult.complete().withDeadline("2026-12-01T00:00:00Z"));
        assertNull(result.getStage());
        assertNull(result.getDeadline());
        assertTrue(result.getError().contains("KA140640"));
    }

    @Test
    void hardFailureDirectsToFailureStageWithErrorState() throws Exception {
        var result = StageDirectorHelper.mapOutput(DIRECTOR, transaction(null),
                ActionResult.hardFailure(new RuntimeException("boom"))
                        .withErrorData(json("{\"code\":42}")));

        assertEquals("failed", result.getStage());
        assertNull(result.getError());
        var updates = result.getStateUpdates();
        assertEquals(2, updates.size());
        assertEquals("/error", updates.get(0).path());
        assertEquals("boom", updates.get(0).value().asText());
        assertEquals("/errorData", updates.get(1).path());
        assertEquals(42, updates.get(1).value().get("code").asInt());
    }

    @Test
    void hardFailureWithCustomStage() {
        var result = StageDirectorHelper.mapOutput(DIRECTOR, transaction(null),
                ActionResult.hardFailure(new RuntimeException("boom")).withCustomStage("special-fail"));
        assertEquals("special-fail", result.getStage());
    }

    @Test
    void hardFailureWithoutFailureStageErrors() {
        var director = new StageDirector("deploy", "/output", "verify", null, null);

        // The handler's own error wins when it supplied one
        var withError = StageDirectorHelper.mapOutput(director, transaction(null),
                ActionResult.hardFailure(new RuntimeException("boom")));
        assertNull(withError.getStage());
        assertEquals("boom", withError.getError());

        // Without a handler error, the missing-failureStage error is synthesized
        var withoutError = StageDirectorHelper.mapOutput(director, transaction(null),
                ActionResult.of(EvalResult.HARD_FAILURE));
        assertNull(withoutError.getStage());
        assertTrue(withoutError.getError().contains("KA140606"));
    }

    @Test
    void transientAndFixableErrorsReportError() {
        for (var actionResult : List.of(
                ActionResult.transientError(new RuntimeException("flaky")),
                ActionResult.fixableError(new RuntimeException("flaky")))) {
            var result = StageDirectorHelper.mapOutput(DIRECTOR, transaction(null), actionResult);
            assertNull(result.getStage());
            assertEquals("flaky", result.getError());
        }
    }

    @Test
    void outputWithoutOutputPathBecomesFixableError() throws Exception {
        var director = new StageDirector("deploy", null, "verify", null, "failed");
        var result = StageDirectorHelper.mapOutput(director, transaction(null),
                ActionResult.complete().withOutput(json("{\"a\":1}")));
        assertNull(result.getStage());
        assertTrue(result.getError().contains("KA140605"));
    }

    @Test
    void extraUpdatesAppendAfterOutput() throws Exception {
        var result = StageDirectorHelper.mapOutput(DIRECTOR, transaction(null),
                ActionResult.complete()
                        .withOutput(json("{\"a\":1}"))
                        .withExtraUpdates(List.of(PatchOp.add("/extra", (Object) true))));
        assertEquals(2, result.getStateUpdates().size());
        assertEquals("/output", result.getStateUpdates().get(0).path());
        assertEquals("/extra", result.getStateUpdates().get(1).path());
    }

    @Test
    void triggersAndEventsCarriedThrough() {
        var result = StageDirectorHelper.mapOutput(DIRECTOR, transaction(null),
                ActionResult.waiting().withTriggers(List.of(new Trigger("topic-a", null))));
        assertEquals("topic-a", result.getTriggers().get(0).topic());
    }

    // ── evalDirected ────────────────────────────────────────────────────────

    public record TestInput(StageDirector stageDirector, String payload) implements WithStageDirector {}

    private static WSHandleTransactions batchOf(WSEvaluateTransaction... transactions) {
        return new WSHandleTransactions(WSMessageType.HANDLE_TRANSACTIONS, "batch-1", null,
                null, "handler-1", null, null, List.of(transactions));
    }

    @Test
    void evalDirectedSynthesizesStageDirectorFromFlatFields() throws Exception {
        var reply = new WSHandleTransactionsResult();
        var batch = batchOf(transaction(json("""
                {"action":"deploy","outputPath":"/out","nextStage":"next","failureStage":"fail","payload":"data"}
                """)));

        Map<String, ActionConfig<TestInput>> actions = Map.of("deploy",
                ActionConfig.parallel((txn, input) -> {
                    assertEquals("data", input.payload());
                    assertEquals("deploy", input.stageDirector().action());
                    assertEquals("/out", input.stageDirector().outputPath());
                    return ActionResult.complete();
                }));

        StageDirectorHelper.evalDirected(reply, batch, actions, TestInput.class);

        assertEquals(1, reply.getResults().size());
        assertEquals("next", reply.getResults().get(0).getStage());
    }

    @Test
    void evalDirectedMissingActionFieldErrors() throws Exception {
        var reply = new WSHandleTransactionsResult();
        var batch = batchOf(transaction(json("{\"payload\":\"data\"}")));

        StageDirectorHelper.evalDirected(reply, batch, Map.<String, ActionConfig<TestInput>>of(), TestInput.class);

        assertEquals(1, reply.getResults().size());
        assertTrue(reply.getResults().get(0).getError().contains("KA140629"));
    }

    @Test
    void evalDirectedNullInputErrors() throws Exception {
        var reply = new WSHandleTransactionsResult();
        var batch = batchOf(transaction(null));

        StageDirectorHelper.evalDirected(reply, batch, Map.<String, ActionConfig<TestInput>>of(), TestInput.class);

        assertTrue(reply.getResults().get(0).getError().contains("KA140628"));
    }

    @Test
    void evalDirectedUnknownActionErrors() throws Exception {
        var reply = new WSHandleTransactionsResult();
        var batch = batchOf(transaction(json("""
                {"action":"mystery","nextStage":"next","failureStage":"fail"}
                """)));

        StageDirectorHelper.evalDirected(reply, batch, Map.<String, ActionConfig<TestInput>>of(), TestInput.class);

        assertTrue(reply.getResults().get(0).getError().contains("Invalid action 'mystery'"));
    }

    @Test
    void evalDirectedBatchMode() throws Exception {
        var reply = new WSHandleTransactionsResult();
        var batch = batchOf(
                transaction(json("{\"action\":\"batch-action\",\"nextStage\":\"n1\",\"failureStage\":\"f\"}")),
                transaction(json("{\"action\":\"batch-action\",\"nextStage\":\"n2\",\"failureStage\":\"f\"}")));

        Map<String, ActionConfig<TestInput>> actions = Map.of("batch-action",
                ActionConfig.batch(inputs -> inputs.stream()
                        .map(in -> ActionResult.complete())
                        .toList()));

        StageDirectorHelper.evalDirected(reply, batch, actions, TestInput.class);

        assertEquals(List.of("n1", "n2"),
                reply.getResults().stream().map(r -> r.getStage()).toList());
    }

    @Test
    void evalDirectedBatchCountMismatchErrorsAll() throws Exception {
        var reply = new WSHandleTransactionsResult();
        var batch = batchOf(
                transaction(json("{\"action\":\"batch-action\",\"nextStage\":\"n\",\"failureStage\":\"f\"}")),
                transaction(json("{\"action\":\"batch-action\",\"nextStage\":\"n\",\"failureStage\":\"f\"}")));

        Map<String, ActionConfig<TestInput>> actions = Map.of("batch-action",
                ActionConfig.batch(inputs -> List.of(ActionResult.complete())));

        StageDirectorHelper.evalDirected(reply, batch, actions, TestInput.class);

        for (var result : reply.getResults()) {
            assertTrue(result.getError().contains("KA140623"));
        }
    }

    @Test
    void evalDirectedHandlerThrowBecomesPerTransactionError() throws Exception {
        var reply = new WSHandleTransactionsResult();
        var batch = batchOf(transaction(json("{\"action\":\"deploy\",\"nextStage\":\"n\",\"failureStage\":\"f\"}")));

        Map<String, ActionConfig<TestInput>> actions = Map.of("deploy",
                ActionConfig.parallel((txn, input) -> {
                    throw new IllegalStateException("handler blew up");
                }));

        StageDirectorHelper.evalDirected(reply, batch, actions, TestInput.class);

        assertEquals("handler blew up", reply.getResults().get(0).getError());
    }

    @Test
    void evalDirectedExplicitStageDirectorInInput() throws Exception {
        var reply = new WSHandleTransactionsResult();
        var batch = batchOf(transaction(json("""
                {"stageDirector":{"action":"deploy","outputPath":"/o","nextStage":"n","failureStage":"f"},"payload":"x"}
                """)));

        Map<String, ActionConfig<TestInput>> actions = Map.of("deploy",
                ActionConfig.parallel((txn, input) -> ActionResult.complete()));

        StageDirectorHelper.evalDirected(reply, batch, actions, TestInput.class);
        assertEquals("n", reply.getResults().get(0).getStage());
    }
}
