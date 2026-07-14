// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.app;

/**
 * Optional one-time setup hook attached to a handler registration. Use it to
 * ensure streams, initialise assets/pools, or any other one-time work. When it
 * runs is governed by {@link io.kaleido.workflowengine.sdk.config.SetupLifecycle}.
 */
@FunctionalInterface
public interface SetupHook {
    void setup(SetupContext context) throws Exception;
}
