// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample;

import io.kaleido.workflowengine.sdk.handlers.EventProcessor;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventProcessorBatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoEventProcessor implements EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EchoEventProcessor.class);

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public void eventProcessorBatch(RequestContext reqContext,
                                    WSEventProcessorBatchResult result,
                                    WSEventProcessorBatchRequest batch) {
        log.info("Event batch received: stream={} streamId={} handler={} eventCount={}",
                batch.streamName(), batch.streamId(), batch.handler(),
                batch.events() != null ? batch.events().size() : 0);

        if (batch.events() != null) {
            for (var event : batch.events()) {
                log.info("  event: topic={} idempotencyKey={} data={}",
                        event.topic(), event.idempotencyKey(), event.data());
            }
        }
    }
}
