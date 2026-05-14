// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.spring;

import io.kaleido.wfe.sdk.handlers.EngineAPI;
import io.kaleido.wfe.sdk.handlers.EventProcessor;
import io.kaleido.wfe.sdk.protocol.WSEventProcessorBatchRequest;
import io.kaleido.wfe.sdk.protocol.WSEventProcessorBatchResult;

/**
 * Wraps an {@link EventProcessor} bean discovered via {@link KaleidoEventProcessor}
 * and supplies the annotation-declared name instead of delegating to {@code name()}.
 */
class AnnotatedEventProcessorAdapter implements EventProcessor {

    private final String handlerName;
    private final EventProcessor delegate;

    AnnotatedEventProcessorAdapter(String handlerName, EventProcessor delegate) {
        this.handlerName = handlerName;
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return handlerName;
    }

    @Override
    public void init(EngineAPI engineAPI) {
        delegate.init(engineAPI);
    }

    @Override
    public WSEventProcessorBatchResult processEvents(WSEventProcessorBatchRequest request) throws Exception {
        return delegate.processEvents(request);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
