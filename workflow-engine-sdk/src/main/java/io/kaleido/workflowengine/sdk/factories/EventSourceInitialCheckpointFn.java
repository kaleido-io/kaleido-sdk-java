// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

/**
 * Builds the initial checkpoint for a new stream from its parsed config.
 */
@FunctionalInterface
public interface EventSourceInitialCheckpointFn<CF> {
    Object buildInitialCheckpoint(CF config) throws Exception;
}
