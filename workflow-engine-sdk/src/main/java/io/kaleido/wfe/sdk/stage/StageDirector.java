// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.stage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StageDirector(
        String action,
        String outputPath,
        String nextStage,
        String failureStage,
        List<ErrorMapEntry> errorMap
) {}
