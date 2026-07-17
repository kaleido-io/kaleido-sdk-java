// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.stage;

import io.kaleido.workflowengine.sdk.protocol.WSEvaluateTransaction;

/**
 * Input for batch directed transaction handling.
 */
public record TransactionHandlerBatchIn<T extends WithStageDirector>(
        WSEvaluateTransaction transaction,
        T value
) {}
