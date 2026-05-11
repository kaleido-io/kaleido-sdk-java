// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.handlers;

import java.util.Arrays;
import java.util.List;

public final class HandlerSetFor {
    private HandlerSetFor() {}

    public static HandlerSet of(Handler... handlers) {
        return engineAPI -> {
            var list = Arrays.asList(handlers);
            for (var h : list) {
                h.init(engineAPI);
            }
            return list;
        };
    }
}
