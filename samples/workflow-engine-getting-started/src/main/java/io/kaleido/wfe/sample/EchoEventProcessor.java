// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sample;

import io.kaleido.wfe.sdk.handlers.EventProcessor;
import io.kaleido.wfe.sdk.protocol.WSEventProcessorBatchRequest;
import io.kaleido.wfe.sdk.protocol.WSEventProcessorBatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoEventProcessor implements EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EchoEventProcessor.class);

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public WSEventProcessorBatchResult processEvents(WSEventProcessorBatchRequest request) {
        log.info("Event batch received: stream={} streamId={} handler={} eventCount={}",
                request.streamName(), request.streamId(), request.handler(),
                request.events() != null ? request.events().size() : 0);

        if (request.events() != null) {
            for (var event : request.events()) {
                log.info("  event: topic={} idempotencyKey={} data={}",
                        event.topic(), event.idempotencyKey(), event.data());
            }
        }

        return WSEventProcessorBatchResult.forRequest(request);
    }
}
