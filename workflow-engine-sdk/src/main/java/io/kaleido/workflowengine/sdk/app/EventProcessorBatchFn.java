// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.app;

import java.util.List;

/**
 * Batch callback for an event processor, invoked for every batch of events
 * received from the workflow engine.
 */
@FunctionalInterface
public interface EventProcessorBatchFn {
    void processBatch(EventProcessorContext context, List<EventProcessorEvent> events) throws Exception;
}
