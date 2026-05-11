// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.handlers;

import io.kaleido.wfe.sdk.protocol.AsyncTransactionInput;
import io.kaleido.wfe.sdk.protocol.WSEngineAPISubmitTransactionsResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EngineAPI {
    CompletableFuture<WSEngineAPISubmitTransactionsResult> submitAsyncTransactions(
            String authRef, List<AsyncTransactionInput> transactions);
}
