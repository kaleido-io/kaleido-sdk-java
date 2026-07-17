// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.stage;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.protocol.HandlerEvent;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.PatchOp;
import io.kaleido.workflowengine.sdk.protocol.Trigger;

import java.util.List;

/**
 * Outcome returned by a directed action handler: the {@link EvalResult} plus
 * optional output, error, triggers, extra state updates, custom stage
 * override, events, and deadline.
 */
public final class ActionResult {

    private final EvalResult result;
    private JsonNode output;
    private Exception error;
    private JsonNode errorData;
    private List<Trigger> triggers;
    private List<PatchOp> extraUpdates;
    private String customStage;
    private String subflow;
    private List<HandlerEvent> events;
    private String deadline;

    private ActionResult(EvalResult result) {
        this.result = result;
    }

    public static ActionResult of(EvalResult result) {
        return new ActionResult(result);
    }

    public static ActionResult complete() {
        return new ActionResult(EvalResult.COMPLETE);
    }

    public static ActionResult waiting() {
        return new ActionResult(EvalResult.WAITING);
    }

    public static ActionResult fixableError(Exception error) {
        return new ActionResult(EvalResult.FIXABLE_ERROR).withError(error);
    }

    public static ActionResult transientError(Exception error) {
        return new ActionResult(EvalResult.TRANSIENT_ERROR).withError(error);
    }

    public static ActionResult hardFailure(Exception error) {
        return new ActionResult(EvalResult.HARD_FAILURE).withError(error);
    }

    public ActionResult withOutput(JsonNode output) {
        this.output = output;
        return this;
    }

    public ActionResult withOutput(Object output) {
        this.output = JSON.MAPPER.valueToTree(output);
        return this;
    }

    public ActionResult withError(Exception error) {
        this.error = error;
        return this;
    }

    public ActionResult withErrorData(JsonNode errorData) {
        this.errorData = errorData;
        return this;
    }

    public ActionResult withTriggers(List<Trigger> triggers) {
        this.triggers = triggers;
        return this;
    }

    public ActionResult withExtraUpdates(List<PatchOp> extraUpdates) {
        this.extraUpdates = extraUpdates;
        return this;
    }

    public ActionResult withCustomStage(String customStage) {
        this.customStage = customStage;
        return this;
    }

    /**
     * Overrides the stage director's {@code nextSubflow} for this action's
     * COMPLETE outcome, routing the transaction into a subflow instead of a
     * plain stage.
     */
    public ActionResult withSubflow(String subflow) {
        this.subflow = subflow;
        return this;
    }

    public ActionResult withEvents(List<HandlerEvent> events) {
        this.events = events;
        return this;
    }

    public ActionResult withDeadline(String deadline) {
        this.deadline = deadline;
        return this;
    }

    public EvalResult result() { return result; }
    public JsonNode output() { return output; }
    public Exception error() { return error; }
    public JsonNode errorData() { return errorData; }
    public List<Trigger> triggers() { return triggers; }
    public List<PatchOp> extraUpdates() { return extraUpdates; }
    public String customStage() { return customStage; }
    public String subflow() { return subflow; }
    public List<HandlerEvent> events() { return events; }
    public String deadline() { return deadline; }
}
