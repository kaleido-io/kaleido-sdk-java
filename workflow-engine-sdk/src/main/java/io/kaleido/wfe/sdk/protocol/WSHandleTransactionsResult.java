// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSHandleTransactionsResult(
        WSMessageType messageType,
        String id,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        List<WSHandleTransactionResult> results
) {
    public static WSHandleTransactionsResult forRequest(WSHandleTransactions request, List<WSHandleTransactionResult> results) {
        return new WSHandleTransactionsResult(
                WSMessageType.HANDLE_TRANSACTIONS_RESULT,
                request.id(), null, request.handler(), null, null,
                results);
    }

    public static WSHandleTransactionsResult error(WSHandleTransactions request, String error) {
        return new WSHandleTransactionsResult(
                WSMessageType.HANDLE_TRANSACTIONS_RESULT,
                request.id(), null, request.handler(), error, null,
                null);
    }
}
