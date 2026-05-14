// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.handlers;

import io.kaleido.wfe.sdk.protocol.WSHandleTransactions;
import io.kaleido.wfe.sdk.protocol.WSHandleTransactionsResult;

/**
 * Handler invoked once per batch of transactions targeted at this provider's
 * transaction handler. Implementations must adhere to the Go SDK contract:
 *
 * <p><strong>Same-length batch invariant:</strong> the returned
 * {@link WSHandleTransactionsResult#results()} list MUST be the same length as
 * {@link WSHandleTransactions#transactions()}, one result per input
 * transaction (in order). If the entire batch fails, return the failure via
 * {@link WSHandleTransactionsResult#error(WSHandleTransactions, String)} (which
 * leaves {@code results} null) rather than returning a different-sized list.
 * The dispatcher rejects mismatched batches with an error response. See
 * {@code asyncHandleWithResponse} in
 * {@code workflow-engine/pkg/enginesdk/handler_runtime.go} and
 * {@code .cursor/plans/go-sdk.md} for the protocol source of truth.
 */
public interface TransactionHandler extends Handler {
    WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) throws Exception;
}
