// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** What a bulk query matched, by type; a type the query did not include is null. */
public record BulkQueryResult(FilterResult<Activity> activities, FilterResult<Address> addresses,
        FilterResult<Asset> assets, FilterResult<Collection> collections, FilterResult<Data> data,
        FilterResult<ActivityEvent> events, FilterResult<Fragment> fragments, FilterResult<Nft> nfts,
        FilterResult<Pool> pools, FilterResult<Transfer> transfers, FilterResult<BalanceChange> balanceChanges) {
}
