// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEngineAPISubmitTransactions(
        WSMessageType messageType,
        String id,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        String activeRequestId,
        String authRef,
        List<AsyncTransactionInput> transactions
) {
    public static WSEngineAPISubmitTransactions of(
            String id, String activeRequestId, String authRef,
            List<AsyncTransactionInput> transactions) {
        return new WSEngineAPISubmitTransactions(
                WSMessageType.ENGINE_API_SUBMIT_TRANSACTIONS,
                id, null, null, null, null,
                activeRequestId, authRef, transactions);
    }
}
