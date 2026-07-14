// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.stage;

/**
 * Configuration for a directed action: its invocation mode and the handler
 * function for that mode.
 */
public record ActionConfig<T extends WithStageDirector>(
        InvocationMode invocationMode,
        TransactionHandlerFn<T> handler,
        TransactionHandlerBatchFn<T> batchHandler
) {
    public static <T extends WithStageDirector> ActionConfig<T> parallel(TransactionHandlerFn<T> handler) {
        return new ActionConfig<>(InvocationMode.PARALLEL, handler, null);
    }

    public static <T extends WithStageDirector> ActionConfig<T> batch(TransactionHandlerBatchFn<T> batchHandler) {
        return new ActionConfig<>(InvocationMode.BATCH, null, batchHandler);
    }
}
