// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sample;

import io.kaleido.wfe.sdk.handlers.EventProcessor;
import io.kaleido.wfe.sdk.protocol.WSEventProcessorBatchRequest;
import io.kaleido.wfe.sdk.protocol.WSEventProcessorBatchResult;
import io.kaleido.wfe.sdk.spring.KaleidoEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@KaleidoEventProcessor("echo")
public class EchoEventProcessor implements EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EchoEventProcessor.class);

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public WSEventProcessorBatchResult processEvents(WSEventProcessorBatchRequest request) {
        log.info("Event batch: stream={} handler={} events={}",
                request.streamName(), request.handler(),
                request.events() != null ? request.events().size() : 0);
        return WSEventProcessorBatchResult.forRequest(request);
    }
}
