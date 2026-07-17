// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.app;

/**
 * Context injected into event processor {@code processBatch} callbacks.
 * Extends {@link SetupContext} with the request-scoped ID for the current batch.
 */
public final class EventProcessorContext extends SetupContext {

    private final String requestId;

    public EventProcessorContext(SetupContext setupContext, String requestId) {
        super(setupContext);
        this.requestId = requestId;
    }

    public String requestId() {
        return requestId;
    }
}
