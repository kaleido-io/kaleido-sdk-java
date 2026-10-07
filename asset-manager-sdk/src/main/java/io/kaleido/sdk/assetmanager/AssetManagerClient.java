// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kaleido.sdk.assetmanager.model.Activity;
import io.kaleido.sdk.assetmanager.model.ActivityEvent;
import io.kaleido.sdk.assetmanager.model.ActivityInput;
import io.kaleido.sdk.assetmanager.model.Address;
import io.kaleido.sdk.assetmanager.model.AddressInput;
import io.kaleido.sdk.assetmanager.model.Asset;
import io.kaleido.sdk.assetmanager.model.AssetInput;
import io.kaleido.sdk.assetmanager.model.Balance;
import io.kaleido.sdk.assetmanager.model.BulkQueryResult;
import io.kaleido.sdk.assetmanager.model.BulkUpsertResult;
import io.kaleido.sdk.assetmanager.model.Collection;
import io.kaleido.sdk.assetmanager.model.CollectionInput;
import io.kaleido.sdk.assetmanager.model.Data;
import io.kaleido.sdk.assetmanager.model.DataInput;
import io.kaleido.sdk.assetmanager.model.EventInput;
import io.kaleido.sdk.assetmanager.model.Fragment;
import io.kaleido.sdk.assetmanager.model.FragmentInput;
import io.kaleido.sdk.assetmanager.model.ItemsResult;
import io.kaleido.sdk.assetmanager.model.Nft;
import io.kaleido.sdk.assetmanager.model.NftInput;
import io.kaleido.sdk.assetmanager.model.Pool;
import io.kaleido.sdk.assetmanager.model.PoolInput;
import io.kaleido.sdk.assetmanager.model.Status;
import io.kaleido.sdk.assetmanager.model.Transfer;
import io.kaleido.sdk.assetmanager.model.TransferInput;
import io.kaleido.sdk.assetmanager.model.UpsertManyResult;
import io.kaleido.sdk.core.http.ServiceClient;
import io.kaleido.sdk.core.http.ServiceClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Typed client for the Asset Manager REST API.
 *
 * <pre>{@code
 * // Inside a workflow engine provider, as the user behind the request:
 * var am = new AssetManagerClient(client.getServiceClientOptions("asset-manager", txn.authRef()));
 *
 * // Outside one, from the asset-manager binding in KALEIDO_CONFIG_FILE:
 * var am = AssetManagerClient.fromConfig();
 *
 * // Or straight to a URL:
 * var am = new AssetManagerClient(new ServiceClientOptions.Http(url, auth, 3, 30_000));
 * }</pre>
 *
 * <p>A lookup by name or id returns empty for a 404; any other status outside 2xx throws
 * {@link io.kaleido.sdk.core.http.ServiceClientException}.
 */
public class AssetManagerClient extends ServiceClient {

    /** The binding name {@link #fromConfig()} resolves. */
    public static final String DEFAULT_BINDING = "asset-manager";

    private static final Logger log = LoggerFactory.getLogger(AssetManagerClient.class);

    private static final TypeReference<ItemsResult<Asset>> ASSETS_PAGE = new TypeReference<>() {};
    private static final TypeReference<ItemsResult<Address>> ADDRESSES_PAGE = new TypeReference<>() {};
    private static final TypeReference<ItemsResult<Pool>> POOLS_PAGE = new TypeReference<>() {};
    private static final TypeReference<ItemsResult<Collection>> COLLECTIONS_PAGE = new TypeReference<>() {};
    private static final TypeReference<ItemsResult<Activity>> ACTIVITIES_PAGE = new TypeReference<>() {};
    private static final TypeReference<ItemsResult<Data>> DATA_PAGE = new TypeReference<>() {};
    private static final TypeReference<ItemsResult<ActivityEvent>> EVENTS_PAGE = new TypeReference<>() {};
    private static final TypeReference<ItemsResult<Fragment>> FRAGMENTS_PAGE = new TypeReference<>() {};
    private static final TypeReference<ItemsResult<Nft>> NFTS_PAGE = new TypeReference<>() {};
    private static final TypeReference<ItemsResult<Transfer>> TRANSFERS_PAGE = new TypeReference<>() {};
    private static final TypeReference<ItemsResult<Balance>> BALANCES_PAGE = new TypeReference<>() {};

    private final String apiPrefix;

    public AssetManagerClient(ServiceClientOptions options) {
        super(options);
        this.apiPrefix = apiPrefix(options);
    }

    /** A client for the {@code asset-manager} binding in the file named by {@code KALEIDO_CONFIG_FILE}. */
    public static AssetManagerClient fromConfig() {
        return fromConfig(DEFAULT_BINDING);
    }

    /** A client for a non-hosted binding in the file named by {@code KALEIDO_CONFIG_FILE}. */
    public static AssetManagerClient fromConfig(String binding) {
        return new AssetManagerClient(ServiceClientOptions.fromConfig(binding));
    }

    /** A client for a non-hosted binding in a config file. */
    public static AssetManagerClient fromConfig(String binding, Path file) {
        return new AssetManagerClient(ServiceClientOptions.fromConfig(binding, file));
    }

    /**
     * The {@code /api/v1} prefix to add to paths: none for a hosted binding or a URL that
     * already includes it.
     */
    static String apiPrefix(ServiceClientOptions options) {
        return switch (options) {
            case ServiceClientOptions.WsProxy proxy -> "";
            case ServiceClientOptions.Http http -> http.url() != null && http.url().contains("/api/v1") ? "" : "/api/v1";
        };
    }

    private String api(String path) {
        return apiPrefix + path;
    }

    private String api(String path, String name) {
        return apiPrefix + path + segment(name);
    }

    /** Encodes a name for a path, keeping {@code /} so qualified names still resolve. */
    static String segment(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    /** A builder that merges repeated objects into one bulk upsert. */
    public BulkUpsertBuilder newBulkUpsertBuilder() {
        return new BulkUpsertBuilder(this);
    }

    public Status getStatus() {
        return get(api("/status"), Status.class);
    }

    // ── Assets ──

    /** The first page of assets. */
    public ItemsResult<Asset> getAssets() {
        return getAssets(Map.of());
    }

    /** The assets matching {@code filter}, sent as query parameters. */
    public ItemsResult<Asset> getAssets(Map<String, ?> filter) {
        return get(api("/assets"), filter, ASSETS_PAGE);
    }

    /** The asset, or empty when there is none. */
    public Optional<Asset> getAsset(String nameOrId) {
        return find(api("/assets/", nameOrId), Map.of(), Asset.class);
    }

    public Asset createAsset(AssetInput input) {
        return post(api("/assets"), input, Asset.class);
    }

    /** Changes only the fields set in {@code updates}. */
    public Asset updateAsset(String nameOrId, AssetInput updates) {
        return patch(api("/assets/", nameOrId), updates, Asset.class);
    }

    public void deleteAsset(String nameOrId) {
        delete(api("/assets/", nameOrId));
    }

    // ── Addresses ──

    /** The first page of addresses. */
    public ItemsResult<Address> getAddresses() {
        return getAddresses(Map.of());
    }

    /** The addresses matching {@code filter}, sent as query parameters. */
    public ItemsResult<Address> getAddresses(Map<String, ?> filter) {
        return get(api("/addresses"), filter, ADDRESSES_PAGE);
    }

    /** The address, or empty when there is none. */
    public Optional<Address> getAddress(String address) {
        return find(api("/addresses/", address), Map.of(), Address.class);
    }

    public Address createAddress(AddressInput input) {
        return post(api("/addresses"), input, Address.class);
    }

    /** Changes only the fields set in {@code updates}. */
    public Address updateAddress(String address, AddressInput updates) {
        return patch(api("/addresses/", address), updates, Address.class);
    }

    public void deleteAddress(String address) {
        delete(api("/addresses/", address));
    }

    // ── Pools ──

    /** The first page of pools. */
    public ItemsResult<Pool> getPools() {
        return getPools(Map.of());
    }

    /** The pools matching {@code filter}, sent as query parameters. */
    public ItemsResult<Pool> getPools(Map<String, ?> filter) {
        return get(api("/pools"), filter, POOLS_PAGE);
    }

    /** The pool, or empty when there is none. */
    public Optional<Pool> getPool(String nameOrId) {
        return find(api("/pools/", nameOrId), Map.of(), Pool.class);
    }

    public Pool createPool(PoolInput input) {
        return post(api("/pools"), input, Pool.class);
    }

    /** Changes only the fields set in {@code updates}. */
    public Pool updatePool(String nameOrId, PoolInput updates) {
        return patch(api("/pools/", nameOrId), updates, Pool.class);
    }

    public void deletePool(String nameOrId) {
        delete(api("/pools/", nameOrId));
    }

    // ── Collections ──

    /** The first page of collections. */
    public ItemsResult<Collection> getCollections() {
        return getCollections(Map.of());
    }

    /** The collections matching {@code filter}, sent as query parameters. */
    public ItemsResult<Collection> getCollections(Map<String, ?> filter) {
        return get(api("/collections"), filter, COLLECTIONS_PAGE);
    }

    /** The collection, or empty when there is none. */
    public Optional<Collection> getCollection(String nameOrId) {
        return find(api("/collections/", nameOrId), Map.of(), Collection.class);
    }

    public Collection createCollection(CollectionInput input) {
        return post(api("/collections"), input, Collection.class);
    }

    /** Changes only the fields set in {@code updates}. */
    public Collection updateCollection(String nameOrId, CollectionInput updates) {
        return patch(api("/collections/", nameOrId), updates, Collection.class);
    }

    public void deleteCollection(String nameOrId) {
        delete(api("/collections/", nameOrId));
    }

    // ── Activities ──

    /** The first page of activities. */
    public ItemsResult<Activity> getActivities() {
        return getActivities(Map.of());
    }

    /** The activities matching {@code filter}, sent as query parameters. */
    public ItemsResult<Activity> getActivities(Map<String, ?> filter) {
        return get(api("/activities"), filter, ACTIVITIES_PAGE);
    }

    /** The activity, or empty when there is none. */
    public Optional<Activity> getActivity(String nameOrId) {
        return find(api("/activities/", nameOrId), Map.of(), Activity.class);
    }

    public Activity createActivity(ActivityInput input) {
        return post(api("/activities"), input, Activity.class);
    }

    /** Changes only the fields set in {@code updates}. */
    public Activity updateActivity(String nameOrId, ActivityInput updates) {
        return patch(api("/activities/", nameOrId), updates, Activity.class);
    }

    public void deleteActivity(String nameOrId) {
        delete(api("/activities/", nameOrId));
    }

    // ── Data ──

    /** The first page of data items. */
    public ItemsResult<Data> getData() {
        return getData(Map.of());
    }

    /** The data items matching {@code filter}, sent as query parameters. */
    public ItemsResult<Data> getData(Map<String, ?> filter) {
        return get(api("/data"), filter, DATA_PAGE);
    }

    /** The data item, or empty when there is none. */
    public Optional<Data> getDataSingle(String nameOrId) {
        return find(api("/data/", nameOrId), Map.of(), Data.class);
    }

    public Data createData(DataInput input) {
        return post(api("/data"), input, Data.class);
    }

    /** Changes only the fields set in {@code updates}. */
    public Data updateData(String nameOrId, DataInput updates) {
        return patch(api("/data/", nameOrId), updates, Data.class);
    }

    public void deleteData(String nameOrId) {
        delete(api("/data/", nameOrId));
    }

    // ── Events ──

    /** The first page of events. */
    public ItemsResult<ActivityEvent> getEvents() {
        return getEvents(Map.of());
    }

    /** The events matching {@code filter}, sent as query parameters. */
    public ItemsResult<ActivityEvent> getEvents(Map<String, ?> filter) {
        return get(api("/events"), filter, EVENTS_PAGE);
    }

    /** The event, or empty when there is none. */
    public Optional<ActivityEvent> getEvent(String nameOrId) {
        return find(api("/events/", nameOrId), Map.of(), ActivityEvent.class);
    }

    public ActivityEvent createEvent(EventInput input) {
        return post(api("/events"), input, ActivityEvent.class);
    }

    /** Changes only the fields set in {@code updates}. */
    public ActivityEvent updateEvent(String nameOrId, EventInput updates) {
        return patch(api("/events/", nameOrId), updates, ActivityEvent.class);
    }

    public void deleteEvent(String nameOrId) {
        delete(api("/events/", nameOrId));
    }

    // ── Fragments ──

    /** The first page of fragments. */
    public ItemsResult<Fragment> getFragments() {
        return getFragments(Map.of());
    }

    /** The fragments matching {@code filter}, sent as query parameters. */
    public ItemsResult<Fragment> getFragments(Map<String, ?> filter) {
        return get(api("/fragments"), filter, FRAGMENTS_PAGE);
    }

    /** The fragment, or empty when there is none. */
    public Optional<Fragment> getFragment(String nameOrId) {
        return find(api("/fragments/", nameOrId), Map.of(), Fragment.class);
    }

    public Fragment createFragment(FragmentInput input) {
        return post(api("/fragments"), input, Fragment.class);
    }

    /** Changes only the fields set in {@code updates}. */
    public Fragment updateFragment(String nameOrId, FragmentInput updates) {
        return patch(api("/fragments/", nameOrId), updates, Fragment.class);
    }

    public void deleteFragment(String nameOrId) {
        delete(api("/fragments/", nameOrId));
    }

    // ── Nfts ──

    /** The first page of NFTs. */
    public ItemsResult<Nft> getNfts() {
        return getNfts(Map.of());
    }

    /** The NFTs matching {@code filter}, sent as query parameters. */
    public ItemsResult<Nft> getNfts(Map<String, ?> filter) {
        return get(api("/nfts"), filter, NFTS_PAGE);
    }

    /** The NFT, or empty when there is none. */
    public Optional<Nft> getNft(String nameOrId) {
        return find(api("/nfts/", nameOrId), Map.of(), Nft.class);
    }

    public Nft createNft(NftInput input) {
        return post(api("/nfts"), input, Nft.class);
    }

    /** Changes only the fields set in {@code updates}. */
    public Nft updateNft(String nameOrId, NftInput updates) {
        return patch(api("/nfts/", nameOrId), updates, Nft.class);
    }

    public void deleteNft(String nameOrId) {
        delete(api("/nfts/", nameOrId));
    }

    // ── Transfers ──

    /** The first page of transfers. */
    public ItemsResult<Transfer> getTransfers() {
        return getTransfers(Map.of());
    }

    /** The transfers matching {@code filter}, sent as query parameters. */
    public ItemsResult<Transfer> getTransfers(Map<String, ?> filter) {
        return get(api("/transfers"), filter, TRANSFERS_PAGE);
    }

    /** The transfer, or empty when there is none. */
    public Optional<Transfer> getTransfer(String transferId) {
        return find(api("/transfers/", transferId), Map.of(), Transfer.class);
    }

    public Transfer createTransfer(TransferInput input) {
        return post(api("/transfers"), input, Transfer.class);
    }

    /** Changes only the fields set in {@code updates}. */
    public Transfer updateTransfer(String transferId, TransferInput updates) {
        return patch(api("/transfers/", transferId), updates, Transfer.class);
    }

    public void deleteTransfer(String transferId) {
        delete(api("/transfers/", transferId));
    }

    // ── Balances ──

    public ItemsResult<Balance> getBalances(Map<String, ?> filter) {
        return get(api("/balances"), filter, BALANCES_PAGE);
    }

    /** The balance, or empty when there is none. */
    public Optional<Balance> getBalance(String nameOrId) {
        return find(api("/balances/", nameOrId), Map.of(), Balance.class);
    }

    public ItemsResult<Balance> getAddressBalances(String address, Map<String, ?> filter) {
        return get(api("/addresses/", address) + "/balances", filter, BALANCES_PAGE);
    }

    public ItemsResult<Balance> getAssetBalances(String assetNameOrId, Map<String, ?> filter) {
        return get(api("/assets/", assetNameOrId) + "/balances", filter, BALANCES_PAGE);
    }

    public ItemsResult<Balance> getPoolBalances(String poolNameOrId, Map<String, ?> filter) {
        return get(api("/pools/", poolNameOrId) + "/balances", filter, BALANCES_PAGE);
    }

    // ── Bulk ──

    /** Answers queries for several types in one request. */
    public BulkQueryResult bulkQuery(BulkQuery query) {
        var started = System.nanoTime();
        var result = post(api("/bulk/query"), query, BulkQueryResult.class);
        log.debug("bulkQuery ({}ms)", (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    /**
     * Creates or updates objects of several types in one request. Each object may appear only
     * once; use {@link #newBulkUpsertBuilder()} to merge repeats.
     */
    public BulkUpsertResult bulkUpsert(BulkUpsert upsert) {
        var started = System.nanoTime();
        var result = put(api("/bulk/datamodel"), upsert, BulkUpsertResult.class);
        if (log.isDebugEnabled() && result != null) {
            log.debug("bulkUpsert{} ({}ms)", summary(result), (System.nanoTime() - started) / 1_000_000);
        }
        return result;
    }

    static String summary(BulkUpsertResult result) {
        var out = new StringJoiner("");
        add(out, "activities", result.activities());
        add(out, "addresses", result.addresses());
        add(out, "assets", result.assets());
        add(out, "collections", result.collections());
        add(out, "data", result.data());
        add(out, "events", result.events());
        add(out, "fragments", result.fragments());
        add(out, "nfts", result.nfts());
        add(out, "pools", result.pools());
        add(out, "transfers", result.transfers());
        return out.toString();
    }

    private static void add(StringJoiner out, String type, UpsertManyResult result) {
        if (result != null && result.size() > 0) {
            out.add(" " + type + "=[c=" + size(result.created()) + ",r=" + size(result.replaced())
                    + ",u=" + size(result.updated()) + ",i=" + size(result.ignored()) + "]");
        }
    }

    private static int size(List<?> items) {
        return items == null ? 0 : items.size();
    }
}
