// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.handlers;

public interface Handler {
    String name();
    default void init(EngineAPI engineAPI) {}
    default void close() {}
}
