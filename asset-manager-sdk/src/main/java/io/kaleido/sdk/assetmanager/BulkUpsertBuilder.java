// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.kaleido.sdk.core.JSON;
import io.kaleido.sdk.core.http.ServiceClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Collects data-model updates into one bulk upsert, touching each object at most once: a
 * repeat of an object it already holds is merged, skipped or replaced (see
 * {@link DuplicateStrategy}). Objects are keyed by name, or by address and name for pools,
 * fragments and NFTs that carry an address, by activity and name for events, by address for
 * addresses, and by protocol id for transfers.
 *
 * <pre>{@code
 * var batch = assetManager.newBulkUpsertBuilder();
 * for (var event : events) {
 *     batch.upsertAddress(new AddressInput().address(event.from()).updateType(CREATE_OR_IGNORE));
 *     batch.upsertTransfer(transferFor(event));
 * }
 * batch.addFinalizer(() -> log.info("indexed {} events", events.size()));
 * batch.execute();
 * }</pre>
 *
 * <p>A builder is single-use: {@link #execute()} clears it whether it succeeds or throws.
 * It is not thread-safe.
 */
public final class BulkUpsertBuilder {

    /** The Asset Manager's error code for a reference to an object that does not exist. */
    static final String INVALID_REFERENCE = "KA090801";

    private final AssetManagerClient client;
    private boolean retryOnInvalidRef = true;
    private Map<String, List<ObjectNode>> updates = new LinkedHashMap<>();
    private final List<Runnable> finalizers = new ArrayList<>();
    private int count;

    public BulkUpsertBuilder(AssetManagerClient client) {
        this.client = client;
    }

    /**
     * Whether {@link #execute()} retries objects one by one, in repeated passes, when the upsert
     * fails because one refers to an object later in the same batch. On by default; turn it off
     * if you order objects yourself and want such a failure to throw at once.
     */
    public BulkUpsertBuilder retryOnInvalidRef(boolean retryOnInvalidRef) {
        this.retryOnInvalidRef = retryOnInvalidRef;
        return this;
    }

    public BulkUpsertBuilder upsertActivity(ActivityInput activity) {
        return upsertActivity(activity, DuplicateStrategy.MERGE);
    }

    public BulkUpsertBuilder upsertActivity(ActivityInput activity, DuplicateStrategy duplicates) {
        return upsert("activities", activity, item -> text(item, "name"), duplicates);
    }

    public BulkUpsertBuilder upsertAddress(AddressInput address) {
        return upsertAddress(address, DuplicateStrategy.MERGE);
    }

    public BulkUpsertBuilder upsertAddress(AddressInput address, DuplicateStrategy duplicates) {
        return upsert("addresses", address, item -> text(item, "address"), duplicates);
    }

    public BulkUpsertBuilder upsertAsset(AssetInput asset) {
        return upsertAsset(asset, DuplicateStrategy.MERGE);
    }

    public BulkUpsertBuilder upsertAsset(AssetInput asset, DuplicateStrategy duplicates) {
        return upsert("assets", asset, item -> text(item, "name"), duplicates);
    }

    public BulkUpsertBuilder upsertCollection(CollectionInput collection) {
        return upsertCollection(collection, DuplicateStrategy.MERGE);
    }

    public BulkUpsertBuilder upsertCollection(CollectionInput collection, DuplicateStrategy duplicates) {
        return upsert("collections", collection, item -> text(item, "name"), duplicates);
    }

    public BulkUpsertBuilder upsertData(DataInput data) {
        return upsertData(data, DuplicateStrategy.MERGE);
    }

    public BulkUpsertBuilder upsertData(DataInput data, DuplicateStrategy duplicates) {
        return upsert("data", data, item -> text(item, "name"), duplicates);
    }

    public BulkUpsertBuilder upsertEvent(EventInput event) {
        return upsertEvent(event, DuplicateStrategy.MERGE);
    }

    public BulkUpsertBuilder upsertEvent(EventInput event, DuplicateStrategy duplicates) {
        return upsert("events", event, item -> scoped(item, "activity"), duplicates);
    }

    public BulkUpsertBuilder upsertFragment(FragmentInput fragment) {
        return upsertFragment(fragment, DuplicateStrategy.MERGE);
    }

    public BulkUpsertBuilder upsertFragment(FragmentInput fragment, DuplicateStrategy duplicates) {
        return upsert("fragments", fragment, item -> scoped(item, "address"), duplicates);
    }

    public BulkUpsertBuilder upsertNft(NftInput nft) {
        return upsertNft(nft, DuplicateStrategy.MERGE);
    }

    public BulkUpsertBuilder upsertNft(NftInput nft, DuplicateStrategy duplicates) {
        return upsert("nfts", nft, item -> scoped(item, "address"), duplicates);
    }

    public BulkUpsertBuilder upsertPool(PoolInput pool) {
        return upsertPool(pool, DuplicateStrategy.MERGE);
    }

    public BulkUpsertBuilder upsertPool(PoolInput pool, DuplicateStrategy duplicates) {
        return upsert("pools", pool, item -> scoped(item, "address"), duplicates);
    }

    public BulkUpsertBuilder upsertTransfer(TransferInput transfer) {
        return upsertTransfer(transfer, DuplicateStrategy.MERGE);
    }

    public BulkUpsertBuilder upsertTransfer(TransferInput transfer, DuplicateStrategy duplicates) {
        return upsert("transfers", transfer, item -> text(item, "protocolId"), duplicates);
    }

    /** Runs after a successful {@link #execute()}, in the order added; not run when it throws. */
    public BulkUpsertBuilder addFinalizer(Runnable finalizer) {
        finalizers.add(finalizer);
        return this;
    }

    public boolean hasUpdates() {
        return count > 0;
    }

    /** How many objects the builder holds. */
    public int count() {
        return count;
    }

    /** The upsert {@link #execute()} would send. */
    public BulkUpsert toBulkUpsert() {
        return toBulkUpsert(updates);
    }

    /**
     * Sends the upsert, if there is anything to send, then runs the finalizers. The builder is
     * cleared afterwards, whether this succeeds or throws.
     *
     * @throws ServiceClientException        when the upsert fails
     * @throws BulkUpsertInvalidRefException when retrying one by one leaves objects that still
     *                                       refer to objects that do not exist
     */
    public void execute() {
        try {
            if (hasUpdates()) {
                try {
                    client.bulkUpsert(toBulkUpsert(updates));
                } catch (ServiceClientException e) {
                    if (!retryOnInvalidRef || !isInvalidReference(e)) {
                        throw e;
                    }
                    retryOneByOne(e);
                }
            }
            finalizers.forEach(Runnable::run);
        } finally {
            updates = new LinkedHashMap<>();
            finalizers.clear();
            count = 0;
        }
    }

    /** Sends each object alone, in passes, until all are written or a pass writes none. */
    private void retryOneByOne(ServiceClientException first) {
        var lastFailure = first;
        while (true) {
            var progressed = false;
            var failed = false;
            for (var entry : updates.entrySet()) {
                var iterator = entry.getValue().iterator();
                while (iterator.hasNext()) {
                    var item = iterator.next();
                    try {
                        client.bulkUpsert(new BulkUpsert().add(entry.getKey(), item));
                        iterator.remove();
                        progressed = true;
                    } catch (ServiceClientException e) {
                        if (!isInvalidReference(e)) {
                            throw e;
                        }
                        lastFailure = e;
                        failed = true;
                    }
                }
            }
            if (!failed) {
                return;
            }
            if (!progressed) {
                throw new BulkUpsertInvalidRefException(toBulkUpsert(updates), lastFailure);
            }
        }
    }

    static boolean isInvalidReference(ServiceClientException e) {
        return (e.getMessage() != null && e.getMessage().contains(INVALID_REFERENCE))
                || (e.body() != null && e.body().contains(INVALID_REFERENCE));
    }

    private BulkUpsertBuilder upsert(String type, Object input, Function<ObjectNode, String> keyOf,
            DuplicateStrategy duplicates) {
        ObjectNode item = JSON.MAPPER.valueToTree(input);
        var items = updates.computeIfAbsent(type, k -> new ArrayList<>());
        var key = keyOf.apply(item);
        var existing = key == null ? -1 : indexOf(items, key, keyOf);
        if (existing < 0) {
            items.add(item);
            count++;
            return this;
        }
        switch (duplicates) {
            case SKIP -> {
            }
            case REPLACE -> items.set(existing, item);
            case MERGE -> merge(items.get(existing), item);
        }
        return this;
    }

    private static int indexOf(List<ObjectNode> items, String key, Function<ObjectNode, String> keyOf) {
        for (var i = 0; i < items.size(); i++) {
            if (key.equals(keyOf.apply(items.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    /** Deep-merges {@code source} into {@code target}: objects field by field, lists concatenated. */
    static void merge(ObjectNode target, ObjectNode source) {
        source.properties().forEach(entry -> {
            var name = entry.getKey();
            JsonNode value = entry.getValue();
            var current = target.get(name);
            if (current instanceof ObjectNode currentObject && value instanceof ObjectNode valueObject) {
                merge(currentObject, valueObject);
            } else if (current instanceof ArrayNode currentArray && value instanceof ArrayNode valueArray) {
                currentArray.addAll(valueArray.deepCopy());
            } else {
                target.set(name, value.deepCopy());
            }
        });
    }

    private static String text(ObjectNode item, String field) {
        var value = item.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    /** The name, prefixed by {@code scope} when the item has one. */
    private static String scoped(ObjectNode item, String scope) {
        var name = text(item, "name");
        var prefix = text(item, scope);
        return name != null && prefix != null ? prefix + ":" + name : name;
    }

    private static BulkUpsert toBulkUpsert(Map<String, List<ObjectNode>> updates) {
        var upsert = new BulkUpsert();
        updates.forEach((type, items) -> items.forEach(item -> upsert.add(type, item.deepCopy())));
        return upsert;
    }
}
