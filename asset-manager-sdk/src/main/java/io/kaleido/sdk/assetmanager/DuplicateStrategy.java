// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

/**
 * What {@link BulkUpsertBuilder} does with an object it already holds, since one bulk upsert
 * may touch each object only once.
 */
public enum DuplicateStrategy {
    /** Deep-merge into the one it holds: objects merge field by field, lists concatenate. */
    MERGE,
    /** Keep the one it holds and drop the new one. */
    SKIP,
    /** Drop the one it holds and keep the new one. */
    REPLACE
}
