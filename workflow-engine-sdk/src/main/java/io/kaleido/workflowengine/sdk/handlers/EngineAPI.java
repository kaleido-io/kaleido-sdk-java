// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.handlers;

import io.kaleido.workflowengine.sdk.protocol.AsyncTransactionInput;
import io.kaleido.workflowengine.sdk.protocol.IdempotentSubmitResult;

import java.util.List;

/**
 * API for handlers to call back into the workflow engine.
 */
public interface EngineAPI {

    /**
     * Submit async transactions to the workflow engine, blocking until the
     * engine responds (handlers run on virtual threads).
     */
    List<IdempotentSubmitResult> submitAsyncTransactions(
            RequestContext reqContext,
            String authRef,
            List<AsyncTransactionInput> transactions) throws Exception;
}
