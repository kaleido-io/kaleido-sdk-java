// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

/**
 * Thrown by {@link BulkUpsertBuilder#execute()} when some objects still refer to objects that do
 * not exist after retrying them one by one: a full pass made no progress.
 */
public class BulkUpsertInvalidRefException extends RuntimeException {

    private final transient BulkUpsert stuck;

    public BulkUpsertInvalidRefException(BulkUpsert stuck, Throwable cause) {
        super("Bulk upsert failed: " + stuck.size() + " object(s) refer to objects that do not exist", cause);
        this.stuck = stuck;
    }

    /** The objects that could not be written. */
    public BulkUpsert stuck() {
        return stuck;
    }
}
