// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Event source config validation result. Mutable: the event source populates
 * {@code initialCheckpoint} (and/or {@code error}) in place.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WSEventSourceValidateConfigResult {
    private WSMessageType messageType = WSMessageType.EVENT_SOURCE_VALIDATE_CONFIG_RESULT;
    private String id;
    private String handler;
    private String error;
    private JsonNode initialCheckpoint;

    public WSEventSourceValidateConfigResult() {}

    public static WSEventSourceValidateConfigResult forRequest(WSEventSourceValidateConfigRequest request) {
        var result = new WSEventSourceValidateConfigResult();
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
    public JsonNode getInitialCheckpoint() { return initialCheckpoint; }
    public void setInitialCheckpoint(JsonNode initialCheckpoint) { this.initialCheckpoint = initialCheckpoint; }
}
