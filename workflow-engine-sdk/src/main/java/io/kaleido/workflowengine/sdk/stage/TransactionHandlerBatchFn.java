// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.stage;

import java.util.List;

/**
 * Function type for handling batch directed transactions.
 */
@FunctionalInterface
public interface TransactionHandlerBatchFn<T extends WithStageDirector> {
    List<ActionResult> handle(List<TransactionHandlerBatchIn<T>> transactions) throws Exception;
}
