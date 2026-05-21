// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.stage;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.kaleido.wfe.sdk.protocol.PatchOp;
import io.kaleido.wfe.sdk.protocol.PatchOpType;
import io.kaleido.wfe.sdk.protocol.Trigger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StageDirectorTest {

    @Test
    void mapOutputComplete() {
        var director = new StageDirector("deploy", "/output/deploy", "verify", "failed", null);
        var output = JsonNodeFactory.instance.objectNode().put("txHash", "0xabc");

        var result = StageDirectorHelper.mapOutput(director, EvalResult.complete(), output);

        assertEquals("verify", result.stage());
        assertNull(result.error());
        assertNotNull(result.stateUpdates());
        assertEquals(1, result.stateUpdates().size());
        assertEquals("/output/deploy", result.stateUpdates().get(0).path());
    }

    @Test
    void mapOutputWaiting() {
        var director = new StageDirector("deploy", "/output", "next", null, null);
        var deadline = Instant.parse("2025-12-01T00:00:00Z");

        var result = StageDirectorHelper.mapOutput(director, EvalResult.waiting(deadline), null);

        assertNull(result.stage());
        assertNull(result.error());
        assertEquals("2025-12-01T00:00:00Z", result.deadline());
    }

    @Test
    void mapOutputHardFailure() {
        var director = new StageDirector("deploy", "/output", "next", "error-stage", null);

        var result = StageDirectorHelper.mapOutput(director, EvalResult.hardFailure("boom"), null);

        assertEquals("error-stage", result.stage());
        assertNull(result.error());
        assertNotNull(result.stateUpdates());
        var errorPatch = result.stateUpdates().stream()
                .filter(p -> "/error".equals(p.path()))
                .findFirst().orElseThrow();
        assertEquals(PatchOpType.REPLACE, errorPatch.op());
        assertEquals("boom", errorPatch.value().asText());
    }

    @Test
    void mapOutputHardFailureWithErrorCodeAndData() {
        var director = new StageDirector("deploy", "/output", "next", "error-stage", null);
        var errorData = JsonNodeFactory.instance.objectNode().put("detail", "something bad");

        var result = StageDirectorHelper.mapOutput(director,
                EvalResult.hardFailure("boom", "ERR_DEPLOY", errorData), null);

        assertEquals("error-stage", result.stage());
        assertNull(result.error());
        assertNotNull(result.stateUpdates());
        assertEquals(3, result.stateUpdates().size());
        assertEquals("/error", result.stateUpdates().get(0).path());
        assertEquals("/errorCode", result.stateUpdates().get(1).path());
        assertEquals("ERR_DEPLOY", result.stateUpdates().get(1).value().asText());
        assertEquals("/errorData", result.stateUpdates().get(2).path());
    }

    @Test
    void mapOutputHardFailureNoFailureStage() {
        var director = new StageDirector("deploy", "/output", "next", null, null);

        var result = StageDirectorHelper.mapOutput(director, EvalResult.hardFailure("boom"), null);

        assertEquals("next", result.stage());
    }

    @Test
    void mapOutputTransientError() {
        var director = new StageDirector("deploy", "/output", "next", null, null);

        var result = StageDirectorHelper.mapOutput(director, EvalResult.transientError("retry me"), null);

        assertNull(result.stage());
        assertEquals("retry me", result.error());
    }

    @Test
    void mapOutputWithTriggers() {
        var director = new StageDirector("deploy", "/output", "next", null, null);
        var triggers = List.of(new Trigger("topic-1", false));

        var evalResult = EvalResult.complete().withTriggers(triggers);
        var result = StageDirectorHelper.mapOutput(director, evalResult, null);

        assertEquals("next", result.stage());
        assertNotNull(result.triggers());
        assertEquals(1, result.triggers().size());
        assertEquals("topic-1", result.triggers().get(0).topic());
    }

    @Test
    void mapOutputWithExtraUpdates() {
        var director = new StageDirector("init", "/output", "next", null, null);
        var output = JsonNodeFactory.instance.objectNode().put("status", "ok");
        var extras = List.of(
                PatchOp.add("/counters", JsonNodeFactory.instance.objectNode()),
                PatchOp.add("/counters/a", JsonNodeFactory.instance.numberNode(1)),
                PatchOp.add("/counters/b", JsonNodeFactory.instance.numberNode(2))
        );

        var evalResult = EvalResult.complete().withExtraUpdates(extras);
        var result = StageDirectorHelper.mapOutput(director, evalResult, output);

        assertEquals("next", result.stage());
        assertNotNull(result.stateUpdates());
        // 1 output patch + 3 extra updates
        assertEquals(4, result.stateUpdates().size());
        assertEquals("/output", result.stateUpdates().get(0).path());
        assertEquals("/counters", result.stateUpdates().get(1).path());
        assertEquals("/counters/a", result.stateUpdates().get(2).path());
        assertEquals("/counters/b", result.stateUpdates().get(3).path());
    }

    @Test
    void mapOutputWithEvents() {
        var director = new StageDirector("emit", "/output", "next", null, null);
        var events = List.of(
                new io.kaleido.wfe.sdk.protocol.HandlerEvent("key-1", "topic.fired", null, null, null)
        );

        var evalResult = EvalResult.complete().withEvents(events);
        var result = StageDirectorHelper.mapOutput(director, evalResult, null);

        assertEquals("next", result.stage());
        assertNotNull(result.events());
        assertEquals(1, result.events().size());
        assertEquals("topic.fired", result.events().get(0).topic());
    }

    @Test
    void mapOutputFixableErrorReturnsError() {
        var director = new StageDirector("fix", "/output", "next", "failed", null);

        var result = StageDirectorHelper.mapOutput(director, EvalResult.fixableError("fix this"), null);

        assertNull(result.stage());
        assertEquals("fix this", result.error());
    }

    @Test
    void mapOutputCompleteWithNoOutput() {
        var director = new StageDirector("noop", "/output", "done", null, null);

        var result = StageDirectorHelper.mapOutput(director, EvalResult.complete(), null);

        assertEquals("done", result.stage());
        assertNull(result.stateUpdates());
    }

    @Test
    void mapOutputCompleteWithNullOutputPath() {
        var director = new StageDirector("noop", null, "done", null, null);
        var output = JsonNodeFactory.instance.objectNode().put("data", "value");

        var result = StageDirectorHelper.mapOutput(director, EvalResult.complete(), output);

        assertEquals("done", result.stage());
        assertNull(result.stateUpdates());
    }
}
