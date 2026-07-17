// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.stage;

import io.kaleido.workflowengine.sdk.protocol.WSEvaluateTransaction;

/**
 * Function type for handling individual directed transactions.
 */
@FunctionalInterface
public interface TransactionHandlerFn<T extends WithStageDirector> {
    ActionResult handle(WSEvaluateTransaction transaction, T input) throws Exception;
}
