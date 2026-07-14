// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Event processor batch response. Mutable: the handler sets {@code error} in
 * place when the batch fails.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WSEventProcessorBatchResult {
    private WSMessageType messageType = WSMessageType.EVENT_PROCESSOR_BATCH_RESULT;
    private String id;
    private String handler;
    private String error;

    public WSEventProcessorBatchResult() {}

    public static WSEventProcessorBatchResult forRequest(WSEventProcessorBatchRequest request) {
        var result = new WSEventProcessorBatchResult();
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
}
