// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.stage;

/**
 * Evaluation outcome of a handler action.
 */
public enum EvalResult {
    FIXABLE_ERROR,
    TRANSIENT_ERROR,
    HARD_FAILURE,
    COMPLETE,
    WAITING
}
