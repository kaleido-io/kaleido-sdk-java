// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.spring;

import io.kaleido.wfe.sdk.handlers.EngineAPI;
import io.kaleido.wfe.sdk.handlers.EventSource;
import io.kaleido.wfe.sdk.protocol.*;

/**
 * Wraps an {@link EventSource} bean discovered via {@link KaleidoEventSource}
 * and supplies the annotation-declared name instead of delegating to {@code name()}.
 */
class AnnotatedEventSourceAdapter implements EventSource {

    private final String handlerName;
    private final EventSource delegate;

    AnnotatedEventSourceAdapter(String handlerName, EventSource delegate) {
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
    public WSEventSourceValidateConfigResult validateConfig(WSEventSourceValidateConfig request) throws Exception {
        return delegate.validateConfig(request);
    }

    @Override
    public WSEventSourcePollResult poll(WSEventSourcePoll request) throws Exception {
        return delegate.poll(request);
    }

    @Override
    public WSEventSourceDeleteResult delete(WSEventSourceDelete request) throws Exception {
        return delegate.delete(request);
    }

    @Override
    public void onConfigChanged(WSEventSourceConfig request) throws Exception {
        delegate.onConfigChanged(request);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
