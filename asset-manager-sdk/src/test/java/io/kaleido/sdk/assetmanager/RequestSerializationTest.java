// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.sdk.assetmanager.model.ActivityInput;
import io.kaleido.sdk.assetmanager.model.AddressInput;
import io.kaleido.sdk.assetmanager.model.AssetInput;
import io.kaleido.sdk.assetmanager.model.BalanceChange;
import io.kaleido.sdk.assetmanager.model.BalanceChangeInput;
import io.kaleido.sdk.assetmanager.model.CollectionInput;
import io.kaleido.sdk.assetmanager.model.ContractManager;
import io.kaleido.sdk.assetmanager.model.DataInput;
import io.kaleido.sdk.assetmanager.model.EventInput;
import io.kaleido.sdk.assetmanager.model.FireFlyLinks;
import io.kaleido.sdk.assetmanager.model.FragmentInput;
import io.kaleido.sdk.assetmanager.model.NftInput;
import io.kaleido.sdk.assetmanager.model.Parent;
import io.kaleido.sdk.assetmanager.model.PoolInput;
import io.kaleido.sdk.assetmanager.model.TransferInput;
import io.kaleido.sdk.core.JSON;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.kaleido.sdk.assetmanager.model.UpdateType.CREATE_ONLY;
import static io.kaleido.sdk.assetmanager.model.UpdateType.CREATE_OR_UPDATE;
import static io.kaleido.sdk.assetmanager.model.UpdateType.UPDATE_ONLY;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RequestSerializationTest {

    private static String json(Object value) {
        return FakeAssetManager.sorted(JSON.MAPPER.valueToTree(value)).toString();
    }

    @Test
    void everyInputFieldIsWrittenUnderItsName() {
        var firefly = new FireFlyLinks("ns", "api1", null, null);

        assertEquals("{\"collection\":\"c\",\"description\":\"d\",\"displayName\":\"A\",\"info\":{\"k\":1},"
                + "\"labels\":{\"l\":\"v\"},\"name\":\"a\",\"updateType\":\"create_only\"}",
                json(new AssetInput().name("a").displayName("A").description("d").info(Map.of("k", 1))
                        .labels(Map.of("l", "v")).collection("c").updateType(CREATE_ONLY)));
        assertEquals("{\"address\":\"0x1\",\"contract\":true,\"contractManager\":{\"build\":\"b\",\"service\":\"s\"},"
                + "\"firefly\":{\"api\":\"api1\",\"namespace\":\"ns\"}}",
                json(new AddressInput().address("0x1").contract(true).contractManager(new ContractManager("s", "b"))
                        .firefly(firefly)));
        assertEquals("{\"address\":\"0x1\",\"asset\":\"a\",\"firefly\":{\"api\":\"api1\",\"namespace\":\"ns\"},"
                + "\"name\":\"p\",\"standard\":\"ERC20\"}",
                json(new PoolInput().name("p").standard("ERC20").firefly(firefly).asset("a").address("0x1")));
        assertEquals("{\"firefly\":{\"data\":\"d1\",\"namespace\":\"ns\"},\"name\":\"d\",\"parent\":{\"ref\":\"0x1/n\","
                + "\"type\":\"nft\"},\"role\":\"r\",\"transactionHash\":\"0xh\",\"uri\":\"u\"}",
                json(new DataInput().name("d").uri("u").transactionHash("0xh").role("r")
                        .firefly(new FireFlyLinks("ns", null, "d1", null)).parent(Parent.nft("0x1/n"))));
        assertEquals("{\"activity\":\"act\",\"name\":\"e\",\"parent\":{\"ref\":\"a\",\"type\":\"asset\"}}",
                json(new EventInput().name("e").activity("act").parent(new Parent("asset", "a"))));
        assertEquals("{\"address\":\"0x1\",\"asset\":\"a\",\"name\":\"f\",\"value\":\"v\",\"valueMasked\":false,"
                + "\"valueReference\":\"r\"}",
                json(new FragmentInput().name("f").value("v").valueMasked(false).valueReference("r").asset("a")
                        .address("0x1")));
        assertEquals("{\"active\":true,\"address\":\"0x1\",\"asset\":\"a\",\"firefly\":{\"namespace\":\"ns\"},"
                + "\"name\":\"n\",\"standard\":\"ERC721\",\"tokenIndex\":\"7\",\"uri\":\"u\"}",
                json(new NftInput().name("n").standard("ERC721").tokenIndex("7").uri("u").active(true)
                        .firefly(new FireFlyLinks("ns", null, null, null)).asset("a").address("0x1")));
        assertEquals("{\"amount\":\"5\",\"balanceChanges\":[{\"address\":\"0x1\",\"amount\":\"5\",\"operation\":"
                + "\"subtract\"}],\"firefly\":{\"blockchainEvent\":\"be\",\"namespace\":\"ns\"},\"from\":\"0x1\","
                + "\"parent\":{\"ref\":\"0x1/p\",\"type\":\"pool\"},\"protocolId\":\"t\",\"signer\":\"0x9\","
                + "\"to\":\"0x2\",\"transactionHash\":\"0xh\",\"type\":\"transfer\",\"updateType\":\"update_only\"}",
                json(new TransferInput().protocolId("t").type("transfer").signer("0x9").from("0x1").to("0x2")
                        .amount("5").firefly(new FireFlyLinks("ns", null, null, "be")).transactionHash("0xh")
                        .balanceChanges(List.of(BalanceChangeInput.subtract("0x1", "5"))).parent(Parent.pool("0x1/p"))
                        .updateType(UPDATE_ONLY)));
        assertEquals("{\"name\":\"c\",\"updateType\":\"create_or_update\"}",
                json(new CollectionInput().name("c").updateType(CREATE_OR_UPDATE)));
        assertEquals("{}", json(new ActivityInput()));
    }

    @Test
    void aBulkUpsertGroupsObjectsByType() {
        var upsert = new BulkUpsert()
                .activity(new ActivityInput().name("act")).address(new AddressInput().address("0x1"))
                .asset(new AssetInput().name("a")).collection(new CollectionInput().name("c"))
                .data(new DataInput().name("d")).event(new EventInput().name("e"))
                .fragment(new FragmentInput().name("f")).nft(new NftInput().name("n"))
                .pool(new PoolInput().name("p")).transfer(new TransferInput().protocolId("t"))
                .asset(new AssetInput().name("b"));

        assertEquals(11, upsert.size());
        assertFalse(upsert.isEmpty());
        assertTrue(new BulkUpsert().isEmpty());
        assertEquals(BulkUpsert.TYPES, upsert.items().keySet().stream().sorted().toList());
        assertEquals(2, upsert.items().get("assets").size());
        assertThrows(UnsupportedOperationException.class, () -> upsert.items().put("x", List.of()));
        assertTrue(upsert.toString().startsWith("BulkUpsert{activities="), upsert.toString());
    }

    @Test
    void aBulkQueryHoldsOneQueryPerType() {
        var all = DataModelQuery.create().limit(1);
        var query = new BulkQuery().activities(all).addresses(all).assets(all).collections(all).data(all)
                .events(all).fragments(all).nfts(all).pools(all).transfers(all).balanceChanges(all);

        JsonNode sent = JSON.MAPPER.valueToTree(query);
        assertEquals(List.of("activities", "addresses", "assets", "collections", "data", "events", "fragments", "nfts",
                "pools", "transfers", "balanceChanges"), List.copyOf(query.queries().keySet()));
        sent.forEach(each -> assertEquals("{\"limit\":1}", each.toString()));
    }

    @Test
    void responsesReadNestedTypes() throws Exception {
        var change = JSON.MAPPER.readValue("{\"id\":\"bc1\",\"address\":\"0x1\",\"operation\":\"add\",\"amount\":\"5\","
                + "\"parent\":{\"type\":\"pool\",\"ref\":\"0x1/p\"},\"balanceBefore\":\"0\",\"balanceAfter\":\"5\","
                + "\"unknown\":true}", BalanceChange.class);

        assertEquals(Parent.pool("0x1/p"), change.parent());
        assertEquals("5", change.balanceAfter());
    }

    @Test
    void aClientFromTheEnvNeedsTheConfigFile() {
        assumeTrue(System.getenv("KALEIDO_CONFIG_FILE") == null, "KALEIDO_CONFIG_FILE is set");

        assertThrows(IllegalStateException.class, AssetManagerClient::fromConfig);
    }
}
