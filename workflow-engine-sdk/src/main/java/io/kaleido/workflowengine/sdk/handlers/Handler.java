// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.handlers;

/**
 * Base contract for all handler types.
 */
public interface Handler {
    String name();

    default void init(EngineAPI engineAPI) throws Exception {}

    default void close() {}
}
