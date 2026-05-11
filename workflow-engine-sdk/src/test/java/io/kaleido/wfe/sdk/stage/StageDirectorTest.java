// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.stage;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.kaleido.wfe.sdk.protocol.Trigger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StageDirectorTest {

    @Test
    void mapOutputComplete() {
        var director = new StageDirector("deploy", "/output/deploy", "verify", "failed");
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
        var director = new StageDirector("deploy", "/output", "next", null);
        var deadline = Instant.parse("2025-12-01T00:00:00Z");

        var result = StageDirectorHelper.mapOutput(director, EvalResult.waiting(deadline), null);

        assertNull(result.stage());
        assertNull(result.error());
        assertEquals("2025-12-01T00:00:00Z", result.deadline());
    }

    @Test
    void mapOutputHardFailure() {
        var director = new StageDirector("deploy", "/output", "next", "error-stage");

        var result = StageDirectorHelper.mapOutput(director, EvalResult.hardFailure("boom"), null);

        assertEquals("error-stage", result.stage());
        assertEquals("boom", result.error());
    }

    @Test
    void mapOutputHardFailureNoFailureStage() {
        var director = new StageDirector("deploy", "/output", "next", null);

        var result = StageDirectorHelper.mapOutput(director, EvalResult.hardFailure("boom"), null);

        assertEquals("next", result.stage());
    }

    @Test
    void mapOutputTransientError() {
        var director = new StageDirector("deploy", "/output", "next", null);

        var result = StageDirectorHelper.mapOutput(director, EvalResult.transientError("retry me"), null);

        assertNull(result.stage());
        assertEquals("retry me", result.error());
    }

    @Test
    void mapOutputWithTriggers() {
        var director = new StageDirector("deploy", "/output", "next", null);
        var triggers = List.of(new Trigger("topic-1", false));

        var evalResult = EvalResult.complete().withTriggers(triggers);
        var result = StageDirectorHelper.mapOutput(director, evalResult, null);

        assertEquals("next", result.stage());
        assertNotNull(result.triggers());
        assertEquals(1, result.triggers().size());
        assertEquals("topic-1", result.triggers().get(0).topic());
    }
}
