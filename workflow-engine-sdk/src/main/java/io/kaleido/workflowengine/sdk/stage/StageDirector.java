// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.stage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Controls action routing and output mapping for a directed transaction:
 * which action to run, where to write its output, and the stages (or
 * subflow) to move to on success or failure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StageDirector(
        String action,
        String outputPath,
        String nextStage,
        String nextSubflow,
        String failureStage
) implements WithStageDirector {

    @Override
    public StageDirector stageDirector() {
        return this;
    }
}
