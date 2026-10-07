// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

import com.fasterxml.jackson.annotation.JsonValue;
import io.kaleido.sdk.assetmanager.model.ActivityInput;
import io.kaleido.sdk.assetmanager.model.AddressInput;
import io.kaleido.sdk.assetmanager.model.AssetInput;
import io.kaleido.sdk.assetmanager.model.CollectionInput;
import io.kaleido.sdk.assetmanager.model.DataInput;
import io.kaleido.sdk.assetmanager.model.EventInput;
import io.kaleido.sdk.assetmanager.model.FragmentInput;
import io.kaleido.sdk.assetmanager.model.NftInput;
import io.kaleido.sdk.assetmanager.model.PoolInput;
import io.kaleido.sdk.assetmanager.model.TransferInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The objects one bulk upsert creates or updates, as is. Each object may appear only once;
 * {@link BulkUpsertBuilder} merges repeats for you.
 */
public final class BulkUpsert {

    static final List<String> TYPES = List.of("activities", "addresses", "assets", "collections", "data", "events",
            "fragments", "nfts", "pools", "transfers");

    private final Map<String, List<Object>> items = new LinkedHashMap<>();

    public BulkUpsert activity(ActivityInput activity) {
        return add("activities", activity);
    }

    public BulkUpsert address(AddressInput address) {
        return add("addresses", address);
    }

    public BulkUpsert asset(AssetInput asset) {
        return add("assets", asset);
    }

    public BulkUpsert collection(CollectionInput collection) {
        return add("collections", collection);
    }

    public BulkUpsert data(DataInput data) {
        return add("data", data);
    }

    public BulkUpsert event(EventInput event) {
        return add("events", event);
    }

    public BulkUpsert fragment(FragmentInput fragment) {
        return add("fragments", fragment);
    }

    public BulkUpsert nft(NftInput nft) {
        return add("nfts", nft);
    }

    public BulkUpsert pool(PoolInput pool) {
        return add("pools", pool);
    }

    public BulkUpsert transfer(TransferInput transfer) {
        return add("transfers", transfer);
    }

    BulkUpsert add(String type, Object item) {
        items.computeIfAbsent(type, k -> new ArrayList<>()).add(item);
        return this;
    }

    /** How many objects this upsert holds. */
    public int size() {
        return items.values().stream().mapToInt(List::size).sum();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /** The objects by type, as they are sent. */
    @JsonValue
    public Map<String, List<Object>> items() {
        return Collections.unmodifiableMap(items);
    }

    @Override
    public String toString() {
        return "BulkUpsert" + items;
    }
}
