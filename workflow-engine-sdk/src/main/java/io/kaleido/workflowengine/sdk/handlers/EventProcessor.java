// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.handlers;

import io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchResult;

/**
 * Event processor handler interface. The handler mutates the supplied
 * {@code result} in place rather than returning a value.
 */
public interface EventProcessor extends Handler {

    void eventProcessorBatch(
            RequestContext reqContext,
            WSEventProcessorBatchResult result,
            WSEventProcessorBatchRequest batch) throws Exception;
}
