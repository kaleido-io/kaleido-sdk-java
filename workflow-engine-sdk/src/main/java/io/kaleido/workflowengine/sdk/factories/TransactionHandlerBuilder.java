// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import io.kaleido.workflowengine.sdk.handlers.TransactionHandler;

/**
 * Fluent builder for configuring a factory-built transaction handler with
 * optional lifecycle hooks. Obtained from
 * {@link TransactionHandlerFactory#createTransactionHandler}.
 */
public interface TransactionHandlerBuilder extends TransactionHandler {
    TransactionHandlerBuilder withInitFn(HandlerInitFn initFn);
    TransactionHandlerBuilder withCloseFn(Runnable closeFn);
}
