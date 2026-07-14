// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.handlers;

import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;

/**
 * Transaction handler interface. The handler mutates the supplied
 * {@code result} in place rather than returning a value.
 */
public interface TransactionHandler extends Handler {

    void transactionHandlerBatch(
            RequestContext reqContext,
            WSHandleTransactionsResult result,
            WSHandleTransactions batch) throws Exception;
}
