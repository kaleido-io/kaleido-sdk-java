// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.handlers;

import io.kaleido.wfe.sdk.protocol.*;

/**
 * Handler implemented by providers that own a polled event source (a stream of
 * external events the engine consumes via {@link WSEventSourcePoll}).
 *
 * <p>Mirrors {@code enginesdk.EventSource} in the Go SDK; see {@code .cursor/plans/go-sdk.md}
 * for the protocol source of truth and {@code wsReceiveLoop} in
 * {@code workflow-engine/pkg/enginesdk/handler_runtime.go} for the dispatch order
 * (config first, polls strictly after, validate/poll/delete may run concurrently).
 *
 * <p>{@link #onConfigChanged(String)} is invoked synchronously by the dispatcher
 * when a {@link WSEventSourceConfig} arrives for a stream that had been
 * configured before. Implementations must complete the call before
 * the next {@link #poll(WSEventSourcePoll)} for the same stream is dispatched.
 */
public interface EventSource extends Handler {
    WSEventSourceValidateConfigResult validateConfig(WSEventSourceValidateConfig request) throws Exception;

    WSEventSourcePollResult poll(WSEventSourcePoll request) throws Exception;

    WSEventSourceDeleteResult delete(WSEventSourceDelete request) throws Exception;

    default void onConfigChanged(String streamId) throws Exception {}
}
