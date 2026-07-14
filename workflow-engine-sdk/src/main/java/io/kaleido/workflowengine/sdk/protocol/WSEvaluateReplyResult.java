// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result for an individual transaction in a batch. Mutable: handlers populate
 * fields on the instance the runtime hands them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WSEvaluateReplyResult {
    private String error;
    private String stage;
    private String subflow;
    private List<PatchOp> stateUpdates;
    private List<Trigger> triggers;
    private List<HandlerEvent> events;
    private String deadline;

    public WSEvaluateReplyResult() {}

    public static WSEvaluateReplyResult error(String error) {
        var result = new WSEvaluateReplyResult();
        result.setError(error);
        return result;
    }

    public static WSEvaluateReplyResult stage(String stage) {
        var result = new WSEvaluateReplyResult();
        result.setStage(stage);
        return result;
    }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getSubflow() { return subflow; }
    public void setSubflow(String subflow) { this.subflow = subflow; }
    public List<PatchOp> getStateUpdates() { return stateUpdates; }
    public void setStateUpdates(List<PatchOp> stateUpdates) { this.stateUpdates = stateUpdates; }
    public List<Trigger> getTriggers() { return triggers; }
    public void setTriggers(List<Trigger> triggers) { this.triggers = triggers; }
    public List<HandlerEvent> getEvents() { return events; }
    public void setEvents(List<HandlerEvent> events) { this.events = events; }
    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
}
