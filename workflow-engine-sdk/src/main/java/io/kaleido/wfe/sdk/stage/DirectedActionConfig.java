// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.stage;

import java.util.List;
import java.util.function.Function;

public record DirectedActionConfig<T extends WithStageDirector>(
        InvocationMode invocationMode,
        Function<T, EvalResult> handler,
        Function<List<T>, List<EvalResult>> batchHandler
) {
    public static <T extends WithStageDirector> DirectedActionConfig<T> parallel(Function<T, EvalResult> handler) {
        return new DirectedActionConfig<>(InvocationMode.PARALLEL, handler, null);
    }

    public static <T extends WithStageDirector> DirectedActionConfig<T> batch(Function<List<T>, List<EvalResult>> handler) {
        return new DirectedActionConfig<>(InvocationMode.BATCH, null, handler);
    }
}
