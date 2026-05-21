// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.stage;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.kaleido.wfe.sdk.protocol.HandlerEvent;
import io.kaleido.wfe.sdk.protocol.PatchOp;
import io.kaleido.wfe.sdk.protocol.PatchOpType;
import io.kaleido.wfe.sdk.protocol.Trigger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvalResultTest {

    @Test
    void completeHasCorrectType() {
        var result = EvalResult.complete();
        assertEquals(EvalResultType.COMPLETE, result.type());
        assertNull(result.message());
        assertNull(result.deadline());
        assertNull(result.triggers());
        assertNull(result.extraUpdates());
        assertNull(result.events());
    }

    @Test
    void waitingHasCorrectType() {
        var result = EvalResult.waiting();
        assertEquals(EvalResultType.WAITING, result.type());
        assertNull(result.deadline());
    }

    @Test
    void waitingWithDeadline() {
        var deadline = Instant.parse("2026-06-01T12:00:00Z");
        var result = EvalResult.waiting(deadline);
        assertEquals(EvalResultType.WAITING, result.type());
        assertEquals(deadline, result.deadline());
    }

    @Test
    void fixableError() {
        var result = EvalResult.fixableError("fix me");
        assertEquals(EvalResultType.FIXABLE_ERROR, result.type());
        assertEquals("fix me", result.message());
    }

    @Test
    void transientError() {
        var result = EvalResult.transientError("try again");
        assertEquals(EvalResultType.TRANSIENT_ERROR, result.type());
        assertEquals("try again", result.message());
    }

    @Test
    void hardFailure() {
        var result = EvalResult.hardFailure("fatal");
        assertEquals(EvalResultType.HARD_FAILURE, result.type());
        assertEquals("fatal", result.message());
    }

    @Test
    void hardFailureWithCodeAndData() {
        var data = JsonNodeFactory.instance.objectNode().put("detail", "info");
        var result = EvalResult.hardFailure("fatal", "ERR_001", data);
        assertEquals(EvalResultType.HARD_FAILURE, result.type());
        assertEquals("fatal", result.message());
        assertEquals("ERR_001", result.errorCode());
        assertEquals(data, result.errorData());
    }

    @Test
    void withTriggers() {
        var triggers = List.of(new Trigger("topic.a", false), new Trigger("topic.b", true));
        var result = EvalResult.complete().withTriggers(triggers);
        assertEquals(EvalResultType.COMPLETE, result.type());
        assertEquals(2, result.triggers().size());
        assertEquals("topic.a", result.triggers().get(0).topic());
        assertEquals("topic.b", result.triggers().get(1).topic());
    }

    @Test
    void withExtraUpdates() {
        var updates = List.of(
                PatchOp.add("/counters", JsonNodeFactory.instance.objectNode()),
                PatchOp.add("/counters/a", JsonNodeFactory.instance.numberNode(1))
        );
        var result = EvalResult.complete().withExtraUpdates(updates);
        assertEquals(2, result.extraUpdates().size());
        assertEquals(PatchOpType.ADD, result.extraUpdates().get(0).op());
        assertEquals("/counters", result.extraUpdates().get(0).path());
    }

    @Test
    void withEvents() {
        var events = List.of(
                new HandlerEvent("key-1", "topic.x", JsonNodeFactory.instance.objectNode(), null, null)
        );
        var result = EvalResult.complete().withEvents(events);
        assertEquals(1, result.events().size());
        assertEquals("topic.x", result.events().get(0).topic());
    }

    @Test
    void withDeadlineModifier() {
        var deadline = Instant.parse("2026-12-31T23:59:59Z");
        var result = EvalResult.waiting().withDeadline(deadline);
        assertEquals(deadline, result.deadline());
    }

    @Test
    void withErrorCode() {
        var result = EvalResult.hardFailure("oops").withErrorCode("ERR_CUSTOM");
        assertEquals("ERR_CUSTOM", result.errorCode());
        assertEquals("oops", result.message());
    }

    @Test
    void withErrorData() {
        var data = JsonNodeFactory.instance.objectNode().put("key", "val");
        var result = EvalResult.hardFailure("oops").withErrorData(data);
        assertEquals(data, result.errorData());
    }

    @Test
    void chainingModifiers() {
        var deadline = Instant.parse("2026-01-01T00:00:00Z");
        var triggers = List.of(new Trigger("t1", false));
        var updates = List.of(PatchOp.add("/x", JsonNodeFactory.instance.numberNode(42)));

        var result = EvalResult.complete()
                .withTriggers(triggers)
                .withExtraUpdates(updates)
                .withDeadline(deadline);

        assertEquals(EvalResultType.COMPLETE, result.type());
        assertEquals(1, result.triggers().size());
        assertEquals(1, result.extraUpdates().size());
        assertEquals(deadline, result.deadline());
    }
}
