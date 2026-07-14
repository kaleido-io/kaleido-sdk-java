// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import io.kaleido.workflowengine.sdk.app.EventProcessorBatchFn;
import io.kaleido.workflowengine.sdk.app.EventProcessorDef;
import io.kaleido.workflowengine.sdk.app.EventProcessorEvent;
import io.kaleido.workflowengine.sdk.app.SetupHook;
import io.kaleido.workflowengine.sdk.handlers.EventProcessor;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Factory for event processor definitions and the runtime adapter that wraps
 * a raw batch function into an {@link EventProcessor}.
 */
public final class EventProcessorFactory {
    private EventProcessorFactory() {}

    private static final Logger log = LoggerFactory.getLogger(EventProcessorFactory.class);

    /**
     * Create an event processor handler definition from a batch function —
     * equivalent to {@link EventProcessorDef#of(EventProcessorBatchFn)}, but
     * consistent with the other factory styles.
     */
    public static EventProcessorDef createEventProcessor(EventProcessorBatchFn batchFn) {
        return EventProcessorDef.of(batchFn);
    }

    /**
     * Create an event processor handler definition from a batch function plus
     * a one-time setup hook.
     */
    public static EventProcessorDef createEventProcessor(EventProcessorBatchFn batchFn, SetupHook setup) {
        return EventProcessorDef.of(batchFn, setup);
    }

    /**
     * Raw batch callback used by {@link #createEventProcessorBase}: receives the
     * per-request context, the typed events, and the request's authRef.
     */
    @FunctionalInterface
    public interface RawBatchFn {
        void processBatch(RequestContext reqContext, List<EventProcessorEvent> events, String authRef)
                throws Exception;
    }

    /**
     * Wrap a raw batch function into an {@link EventProcessor} for the runtime.
     * Used by the client's builder registration — not usually called directly.
     * Batch failures set {@code result.error} rather than propagating.
     */
    public static EventProcessor createEventProcessorBase(String name, RawBatchFn batchFn) {
        return new EventProcessor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void eventProcessorBatch(RequestContext reqContext,
                                            WSEventProcessorBatchResult result,
                                            WSEventProcessorBatchRequest batch) {
                try {
                    var events = batch.events() == null
                            ? List.<EventProcessorEvent>of()
                            : batch.events().stream()
                                    .map(evt -> new EventProcessorEvent(evt.idempotencyKey(), evt.topic(), evt.data()))
                                    .toList();
                    batchFn.processBatch(reqContext, events, batch.authRef());
                } catch (Exception e) {
                    log.error("Event processor batch failed", e);
                    result.setError(e.getMessage());
                }
            }
        };
    }
}
