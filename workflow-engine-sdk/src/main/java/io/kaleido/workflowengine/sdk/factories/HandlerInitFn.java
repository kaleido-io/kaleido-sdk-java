// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import io.kaleido.workflowengine.sdk.handlers.EngineAPI;

/**
 * Optional initialization callback for factory-built handlers, invoked once
 * before the WebSocket connection is established.
 */
@FunctionalInterface
public interface HandlerInitFn {
    void init(EngineAPI engineAPI) throws Exception;
}
