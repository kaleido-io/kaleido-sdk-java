// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import io.kaleido.workflowengine.sdk.handlers.EventSource;

/**
 * Fluent builder for configuring an event source with optional lifecycle hooks.
 * Obtained from {@link EventSourceFactory#createEventSource}.
 */
public interface EventSourceBuilder<CF> extends EventSource {
    EventSourceBuilder<CF> withDeleteFn(EventSourceDeleteFn deleteFn);
    EventSourceBuilder<CF> withConfigParser(EventSourceConfigParserFn<CF> parserFn);
    EventSourceBuilder<CF> withInitialCheckpoint(EventSourceInitialCheckpointFn<CF> buildFn);
    EventSourceBuilder<CF> withInitFn(HandlerInitFn initFn);
    EventSourceBuilder<CF> withCloseFn(Runnable closeFn);
}
