// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** What a bulk upsert did, by type; a type the upsert did not include is null. */
public record BulkUpsertResult(UpsertManyResult activities, UpsertManyResult addresses, UpsertManyResult assets,
        UpsertManyResult collections, UpsertManyResult data, UpsertManyResult events, UpsertManyResult fragments,
        UpsertManyResult nfts, UpsertManyResult pools, UpsertManyResult transfers) {
}
