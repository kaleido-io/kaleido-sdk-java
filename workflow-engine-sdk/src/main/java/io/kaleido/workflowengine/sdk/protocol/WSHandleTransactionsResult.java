// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Transaction handling response (batch evaluate results). Mutable: the runtime
 * constructs it and the handler populates {@code results} (and/or {@code error})
 * in place.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WSHandleTransactionsResult {
    private WSMessageType messageType = WSMessageType.HANDLE_TRANSACTIONS_RESULT;
    private String id;
    private String handler;
    private String error;
    private List<WSEvaluateReplyResult> results = new ArrayList<>();

    public WSHandleTransactionsResult() {}

    public static WSHandleTransactionsResult forRequest(WSHandleTransactions request) {
        var result = new WSHandleTransactionsResult();
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
    public List<WSEvaluateReplyResult> getResults() { return results; }
    public void setResults(List<WSEvaluateReplyResult> results) { this.results = results; }
}
