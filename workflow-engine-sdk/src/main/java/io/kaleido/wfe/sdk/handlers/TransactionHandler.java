// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.handlers;

import io.kaleido.wfe.sdk.protocol.WSHandleTransactions;
import io.kaleido.wfe.sdk.protocol.WSHandleTransactionsResult;

public interface TransactionHandler extends Handler {
    WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) throws Exception;
}
