// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.stage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Maps a regex pattern to an error classification. When a handler throws an
 * exception whose message matches {@link #pattern()}, the SDK classifies the
 * error as the specified {@link #type()} instead of propagating it as-is.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorMapEntry(
        String pattern,
        EvalResultType type
) {}
