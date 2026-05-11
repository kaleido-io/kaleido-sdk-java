// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.handlers;

import java.util.List;

public interface HandlerSet {
    List<Handler> init(EngineAPI engineAPI);
    default void close() {}
}
