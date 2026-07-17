// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.app;

import io.kaleido.workflowengine.sdk.handlers.TransactionHandler;

/**
 * Registration for a transaction handler: the pre-built handler plus an
 * optional setup hook.
 */
public record TransactionHandlerRegistration(
        SetupHook setup,
        TransactionHandler handler
) {
    public static TransactionHandlerRegistration of(TransactionHandler handler) {
        return new TransactionHandlerRegistration(null, handler);
    }

    public static TransactionHandlerRegistration of(TransactionHandler handler, SetupHook setup) {
        return new TransactionHandlerRegistration(setup, handler);
    }
}
