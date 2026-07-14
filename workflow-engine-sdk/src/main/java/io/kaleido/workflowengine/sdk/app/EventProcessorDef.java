// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.app;

/**
 * Handler definition for an event processor.
 *
 * <ul>
 *   <li>{@code setup} — optional; runs once per the configured setup lifecycle.</li>
 *   <li>{@code processBatch} — called for every batch of events received from
 *       the workflow engine.</li>
 * </ul>
 */
public record EventProcessorDef(
        SetupHook setup,
        EventProcessorBatchFn processBatch
) {
    public static EventProcessorDef of(EventProcessorBatchFn processBatch) {
        return new EventProcessorDef(null, processBatch);
    }

    public static EventProcessorDef of(EventProcessorBatchFn processBatch, SetupHook setup) {
        return new EventProcessorDef(setup, processBatch);
    }
}
