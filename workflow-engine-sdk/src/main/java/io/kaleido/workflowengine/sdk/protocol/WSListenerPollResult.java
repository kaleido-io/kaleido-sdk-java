// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener poll result. Mutable: the event source populates {@code events} and
 * {@code checkpoint} in place.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WSListenerPollResult {
    private WSMessageType messageType = WSMessageType.EVENT_SOURCE_POLL_RESULT;
    private String id;
    private String handler;
    private String error;
    private JsonNode checkpoint;
    private List<ListenerEvent> events = new ArrayList<>();

    public WSListenerPollResult() {}

    public static WSListenerPollResult forRequest(WSListenerPollRequest request) {
        var result = new WSListenerPollResult();
        result.setId(request.id());
        result.setHandler(request.handler());
        return result;
    }

    public WSMessageType getMessageType() { return messageType; }
    public void setMessageType(WSMessageType messageType) { this.messageType = messageType; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHandler() { return handler; }
    public void setHandler(String handler) { this.handler = handler; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public JsonNode getCheckpoint() { return checkpoint; }
    public void setCheckpoint(JsonNode checkpoint) { this.checkpoint = checkpoint; }
    public List<ListenerEvent> getEvents() { return events; }
    public void setEvents(List<ListenerEvent> events) { this.events = events; }
}
