// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.stage;

/**
 * Interface for input types that carry StageDirector routing metadata.
 * A record with a {@code StageDirector stageDirector} component implements
 * this automatically.
 */
public interface WithStageDirector {
    StageDirector stageDirector();
}
