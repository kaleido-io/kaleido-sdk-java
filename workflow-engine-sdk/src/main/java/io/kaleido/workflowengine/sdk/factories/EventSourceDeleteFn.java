// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import io.kaleido.workflowengine.sdk.protocol.WSEventStreamInfo;

/**
 * Cleanup callback invoked when an event source stream is removed.
 */
@FunctionalInterface
public interface EventSourceDeleteFn {
    void delete(WSEventStreamInfo info) throws Exception;
}
