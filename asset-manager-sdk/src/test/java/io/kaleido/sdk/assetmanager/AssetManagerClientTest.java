// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

import io.kaleido.sdk.assetmanager.model.ActivityInput;
import io.kaleido.sdk.assetmanager.model.AddressInput;
import io.kaleido.sdk.assetmanager.model.Asset;
import io.kaleido.sdk.assetmanager.model.AssetInput;
import io.kaleido.sdk.assetmanager.model.BalanceChangeInput;
import io.kaleido.sdk.assetmanager.model.CollectionInput;
import io.kaleido.sdk.assetmanager.model.DataInput;
import io.kaleido.sdk.assetmanager.model.EventInput;
import io.kaleido.sdk.assetmanager.model.FragmentInput;
import io.kaleido.sdk.assetmanager.model.NftInput;
import io.kaleido.sdk.assetmanager.model.Parent;
import io.kaleido.sdk.assetmanager.model.PoolInput;
import io.kaleido.sdk.assetmanager.model.TransferInput;
import io.kaleido.sdk.assetmanager.model.UpdateType;
import io.kaleido.sdk.core.config.ServiceBindingAuth;
import io.kaleido.sdk.core.http.ServiceClientException;
import io.kaleido.sdk.core.http.ServiceClientOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.kaleido.sdk.assetmanager.FakeAssetManager.ok;
import static io.kaleido.sdk.assetmanager.FakeAssetManager.status;
import static org.junit.jupiter.api.Assertions.*;

class AssetManagerClientTest {

    private final FakeAssetManager am = new FakeAssetManager();

    // ── Paths ───────────────────────────────────────────────────────────────

    @Test
    void theApiPrefixDependsOnHowTheServiceIsReached() {
        var auth = ServiceBindingAuth.basic("u", "p");
        assertEquals("", AssetManagerClient.apiPrefix(new ServiceClientOptions.WsProxy(am, "T", "id", null)));
        assertEquals("/api/v1", AssetManagerClient.apiPrefix(
                new ServiceClientOptions.Http("https://host/endpoint/e1/s1/rest", auth, null, null)));
        assertEquals("", AssetManagerClient.apiPrefix(
                new ServiceClientOptions.Http("http://am.svc:5000/api/v1/namespaces/s1", auth, null, null)));
        assertEquals("/api/v1", AssetManagerClient.apiPrefix(new ServiceClientOptions.Http(null, auth, null, null)));
    }

    @Test
    void namesAreEncodedInPathsButSlashesKept() {
        assertEquals("my%20bond", AssetManagerClient.segment("my bond"));
        assertEquals("0xabc/pool-1", AssetManagerClient.segment("0xabc/pool-1"));
        assertEquals("a%3Fb%23c%25", AssetManagerClient.segment("a?b#c%"));
    }

    @Test
    void aClientFromConfigGoesStraightToTheBindingUrl(@TempDir Path dir) throws IOException {
        var file = Files.writeString(dir.resolve("config.yaml"), """
                service-bindings:
                  asset-manager:
                    url: http://127.0.0.1:1/rest
                    auth: {type: basic, username: u, password: p}
                  other-am:
                    url: http://127.0.0.1:1/api/v1/namespaces/s1
                    auth: {type: basic, username: u, password: p}
                """);

        assertNotNull(AssetManagerClient.fromConfig(AssetManagerClient.DEFAULT_BINDING, file));
        assertNotNull(AssetManagerClient.fromConfig("other-am", file));
        assertThrows(IllegalArgumentException.class, () -> AssetManagerClient.fromConfig("missing", file));
    }

    // ── Reads and writes ────────────────────────────────────────────────────

    @Test
    void assetsAreListedFetchedCreatedUpdatedAndDeleted() {
        am.answer = request -> switch (request.method()) {
            case "GET" -> request.path().equals("/assets?label=x")
                    ? ok("{\"count\":1,\"total\":5,\"items\":[{\"id\":\"a1\",\"name\":\"bond\",\"labels\":{\"k\":\"v\"}}]}")
                    : ok("{\"id\":\"a1\",\"name\":\"bond\",\"info\":{\"isin\":\"X1\"}}");
            case "DELETE" -> status(204, "");
            default -> ok("{\"id\":\"a1\",\"name\":\"bond\"}");
        };
        var client = am.client();

        var page = client.getAssets(Map.of("label", "x"));
        var asset = client.getAsset("bond").orElseThrow();
        var created = client.createAsset(new AssetInput().name("bond").displayName("Bond")
                .labels(Map.of("k", "v")).info(Map.of("isin", "X1")));
        client.updateAsset("bond", new AssetInput().description("updated"));
        client.deleteAsset("bond");

        assertEquals(1, page.count());
        assertEquals(5, page.total());
        assertEquals(new Asset("a1", "bond", null, null, null, null, Map.of("k", "v"), null, null), page.items().getFirst());
        assertEquals("X1", asset.info().get("isin").asText());
        assertEquals("a1", created.id());
        assertEquals(List.of(
                "GET /assets?label=x",
                "GET /assets/bond",
                "POST /assets {\"displayName\":\"Bond\",\"info\":{\"isin\":\"X1\"},\"labels\":{\"k\":\"v\"},\"name\":\"bond\"}",
                "PATCH /assets/bond {\"description\":\"updated\"}",
                "DELETE /assets/bond"), am.sent());
    }

    @Test
    void aLookupOfSomethingMissingIsEmptyButOtherErrorsThrow() {
        am.answer = request -> request.path().endsWith("/missing") ? status(404, "{}") : status(500, "boom");
        var client = am.client();

        assertTrue(client.getAsset("missing").isEmpty());
        assertTrue(client.getTransfer("missing").isEmpty());
        assertEquals(500, assertThrows(ServiceClientException.class, () -> client.getPool("p")).status());
    }

    @Test
    void everyTypeUsesItsOwnPath() {
        am.answer = request -> ok(request.path().contains("?") || !request.path().matches(".*/[^/]+/[^/]+$")
                ? "{\"count\":0,\"items\":[]}" : "{}");
        var client = am.client();

        client.getAddresses();
        client.getAddress("0x1");
        client.createAddress(new AddressInput().address("0x1").contract(true));
        client.updateAddress("0x1", new AddressInput().displayName("A"));
        client.deleteAddress("0x1");
        client.getPools();
        client.getPool("0x1/p");
        client.createPool(new PoolInput().name("p").asset("a").address("0x1").standard("ERC20"));
        client.updatePool("p", new PoolInput().displayName("P"));
        client.deletePool("p");
        client.getCollections();
        client.getCollection("c");
        client.createCollection(new CollectionInput().name("c"));
        client.updateCollection("c", new CollectionInput().description("d"));
        client.deleteCollection("c");
        client.getActivities();
        client.getActivity("act");
        client.createActivity(new ActivityInput().name("act"));
        client.updateActivity("act", new ActivityInput().displayName("Act"));
        client.deleteActivity("act");
        client.getData();
        client.getDataSingle("d");
        client.createData(new DataInput().name("d").parent(new Parent("asset", "a")));
        client.updateData("d", new DataInput().uri("ipfs://x"));
        client.deleteData("d");
        client.getEvents();
        client.getEvent("e");
        client.createEvent(new EventInput().name("e").activity("act"));
        client.updateEvent("e", new EventInput().displayName("E"));
        client.deleteEvent("e");
        client.getFragments();
        client.getFragment("f");
        client.createFragment(new FragmentInput().name("f").value("v").valueMasked(true));
        client.updateFragment("f", new FragmentInput().valueReference("r"));
        client.deleteFragment("f");
        client.getNfts();
        client.getNft("n");
        client.createNft(new NftInput().name("n").tokenIndex("1").active(true));
        client.updateNft("n", new NftInput().uri("ipfs://n"));
        client.deleteNft("n");
        client.getTransfers();
        client.getTransfer("t");
        client.createTransfer(new TransferInput().protocolId("t").transactionHash("0xh").amount("5")
                .balanceChanges(List.of(BalanceChangeInput.add("0x2", "5"))).parent(Parent.pool("0x1/p")));
        client.updateTransfer("t", new TransferInput().description("x"));
        client.deleteTransfer("t");

        assertEquals(List.of(
                "GET /addresses", "GET /addresses/0x1",
                "POST /addresses {\"address\":\"0x1\",\"contract\":true}",
                "PATCH /addresses/0x1 {\"displayName\":\"A\"}", "DELETE /addresses/0x1",
                "GET /pools", "GET /pools/0x1/p",
                "POST /pools {\"address\":\"0x1\",\"asset\":\"a\",\"name\":\"p\",\"standard\":\"ERC20\"}",
                "PATCH /pools/p {\"displayName\":\"P\"}", "DELETE /pools/p",
                "GET /collections", "GET /collections/c", "POST /collections {\"name\":\"c\"}",
                "PATCH /collections/c {\"description\":\"d\"}", "DELETE /collections/c",
                "GET /activities", "GET /activities/act", "POST /activities {\"name\":\"act\"}",
                "PATCH /activities/act {\"displayName\":\"Act\"}", "DELETE /activities/act",
                "GET /data", "GET /data/d", "POST /data {\"name\":\"d\",\"parent\":{\"ref\":\"a\",\"type\":\"asset\"}}",
                "PATCH /data/d {\"uri\":\"ipfs://x\"}", "DELETE /data/d",
                "GET /events", "GET /events/e", "POST /events {\"activity\":\"act\",\"name\":\"e\"}",
                "PATCH /events/e {\"displayName\":\"E\"}", "DELETE /events/e",
                "GET /fragments", "GET /fragments/f", "POST /fragments {\"name\":\"f\",\"value\":\"v\",\"valueMasked\":true}",
                "PATCH /fragments/f {\"valueReference\":\"r\"}", "DELETE /fragments/f",
                "GET /nfts", "GET /nfts/n", "POST /nfts {\"active\":true,\"name\":\"n\",\"tokenIndex\":\"1\"}",
                "PATCH /nfts/n {\"uri\":\"ipfs://n\"}", "DELETE /nfts/n",
                "GET /transfers", "GET /transfers/t",
                "POST /transfers {\"amount\":\"5\",\"balanceChanges\":[{\"address\":\"0x2\",\"amount\":\"5\","
                        + "\"operation\":\"add\"}],\"parent\":{\"ref\":\"0x1/p\",\"type\":\"pool\"},\"protocolId\":\"t\","
                        + "\"transactionHash\":\"0xh\"}",
                "PATCH /transfers/t {\"description\":\"x\"}", "DELETE /transfers/t"), am.sent());
    }

    @Test
    void balancesAndStatus() {
        am.answer = request -> request.path().equals("/status") ? ok("{\"status\":\"ok\"}")
                : request.path().equals("/balances/b1") ? ok("{\"id\":\"b1\",\"balanceAfter\":\"7\"}")
                : ok("{\"count\":1,\"items\":[{\"id\":\"b1\",\"address\":\"0x1\",\"balanceAfter\":\"7\"}]}");
        var client = am.client();

        assertEquals("ok", client.getStatus().status());
        assertEquals("7", client.getBalance("b1").orElseThrow().balanceAfter());
        assertEquals("0x1", client.getBalances(Map.of()).items().getFirst().address());
        client.getAddressBalances("0x1", Map.of("limit", 5));
        client.getAssetBalances("bond", Map.of());
        client.getPoolBalances("0x1/p", Map.of());

        assertEquals(List.of("GET /status", "GET /balances/b1", "GET /balances", "GET /addresses/0x1/balances?limit=5",
                "GET /assets/bond/balances", "GET /pools/0x1/p/balances"), am.sent());
    }

    // ── Bulk ────────────────────────────────────────────────────────────────

    @Test
    void aBulkUpsertSendsEachTypeAndReadsWhatHappened() {
        am.answer = request -> ok("""
                {"assets":{"created":[{"name":"bond","id":"a1"}]},
                 "pools":{"ignored":[{"name":"p","id":"p1","parent":"0x1"}]}}""");
        var upsert = new BulkUpsert()
                .asset(new AssetInput().name("bond").updateType(UpdateType.CREATE_OR_IGNORE))
                .pool(new PoolInput().name("p").asset("bond").updateType(UpdateType.CREATE_OR_REPLACE));

        var result = am.client().bulkUpsert(upsert);

        assertEquals(2, upsert.size());
        assertEquals(List.of("PUT /bulk/datamodel {\"assets\":[{\"name\":\"bond\",\"updateType\":\"create_or_ignore\"}],"
                + "\"pools\":[{\"asset\":\"bond\",\"name\":\"p\",\"updateType\":\"create_or_replace\"}]}"), am.sent());
        assertEquals("a1", result.assets().created().getFirst().id());
        assertEquals("0x1", result.pools().ignored().getFirst().parent());
        assertNull(result.transfers());
        assertEquals(" assets=[c=1,r=0,u=0,i=0] pools=[c=0,r=0,u=0,i=1]", AssetManagerClient.summary(result));
    }

    @Test
    void aBulkQuerySendsEachQueryAndReadsTypedResults() {
        am.answer = request -> ok("""
                {"assets":{"count":1,"allItems":true,"items":[{"id":"a1","name":"bond"}]},
                 "transfers":{"count":0,"total":0,"allItems":true,"items":[]}}""");

        var result = am.client().bulkQuery(new BulkQuery()
                .assets(DataModelQuery.create().eq("name", "bond"))
                .transfers(DataModelQuery.create().limit(10).count(true)));

        assertEquals(List.of("POST /bulk/query {\"assets\":{\"eq\":[{\"field\":\"name\",\"value\":\"bond\"}]},"
                + "\"transfers\":{\"count\":true,\"limit\":10}}"), am.sent());
        assertEquals("bond", result.assets().items().getFirst().name());
        assertTrue(result.assets().allItems());
        assertEquals(0, result.transfers().total());
        assertNull(result.pools());
    }
}
