// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.sdk.assetmanager.model.ActivityInput;
import io.kaleido.sdk.assetmanager.model.AddressInput;
import io.kaleido.sdk.assetmanager.model.AssetInput;
import io.kaleido.sdk.assetmanager.model.BalanceChangeInput;
import io.kaleido.sdk.assetmanager.model.CollectionInput;
import io.kaleido.sdk.assetmanager.model.DataInput;
import io.kaleido.sdk.assetmanager.model.EventInput;
import io.kaleido.sdk.assetmanager.model.FragmentInput;
import io.kaleido.sdk.assetmanager.model.NftInput;
import io.kaleido.sdk.assetmanager.model.PoolInput;
import io.kaleido.sdk.assetmanager.model.TransferInput;
import io.kaleido.sdk.core.JSON;
import io.kaleido.sdk.core.http.ServiceClientException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.kaleido.sdk.assetmanager.FakeAssetManager.ok;
import static io.kaleido.sdk.assetmanager.FakeAssetManager.status;
import static io.kaleido.sdk.assetmanager.model.UpdateType.CREATE_OR_IGNORE;
import static org.junit.jupiter.api.Assertions.*;

class BulkUpsertBuilderTest {

    private final FakeAssetManager am = new FakeAssetManager();
    private final BulkUpsertBuilder builder = am.client().newBulkUpsertBuilder();

    private static String json(BulkUpsert upsert) {
        return FakeAssetManager.sorted(JSON.MAPPER.valueToTree(upsert)).toString();
    }

    // ── Duplicates ──────────────────────────────────────────────────────────

    @Test
    void aRepeatIsDeepMergedByDefault() {
        builder.upsertAsset(new AssetInput().name("bond").displayName("Bond")
                .labels(Map.of("a", "1")).info(Map.of("tags", List.of("x"), "meta", Map.of("k", "v"))));
        builder.upsertAsset(new AssetInput().name("bond").description("A bond")
                .labels(Map.of("b", "2")).info(Map.of("tags", List.of("y"), "meta", Map.of("k2", "v2"))));

        assertEquals(1, builder.count());
        assertEquals("{\"assets\":[{\"description\":\"A bond\",\"displayName\":\"Bond\",\"info\":{\"meta\":{\"k\":\"v\","
                + "\"k2\":\"v2\"},\"tags\":[\"x\",\"y\"]},\"labels\":{\"a\":\"1\",\"b\":\"2\"},\"name\":\"bond\"}]}",
                json(builder.toBulkUpsert()));
    }

    @Test
    void aLaterValueWinsAndNeverChangesWhatWasPassedIn() {
        var first = new TransferInput().protocolId("t1").amount("1")
                .balanceChanges(List.of(BalanceChangeInput.add("0xa", "1")));
        builder.upsertTransfer(first);
        builder.upsertTransfer(new TransferInput().protocolId("t1").amount("2")
                .balanceChanges(List.of(BalanceChangeInput.subtract("0xb", "1"))));

        assertEquals("{\"transfers\":[{\"amount\":\"2\",\"balanceChanges\":[{\"address\":\"0xa\",\"amount\":\"1\","
                + "\"operation\":\"add\"},{\"address\":\"0xb\",\"amount\":\"1\",\"operation\":\"subtract\"}],"
                + "\"protocolId\":\"t1\"}]}", json(builder.toBulkUpsert()));
        assertEquals("1", JSON.MAPPER.<JsonNode>valueToTree(first).get("amount").asText());
    }

    @Test
    void skipKeepsTheFirstAndReplaceKeepsTheLast() {
        builder.upsertAddress(new AddressInput().address("0x1").displayName("first"));
        builder.upsertAddress(new AddressInput().address("0x1").displayName("skipped"), DuplicateStrategy.SKIP);
        builder.upsertCollection(new CollectionInput().name("c").displayName("first").description("gone"));
        builder.upsertCollection(new CollectionInput().name("c").displayName("last"), DuplicateStrategy.REPLACE);

        assertEquals(2, builder.count());
        assertEquals("{\"addresses\":[{\"address\":\"0x1\",\"displayName\":\"first\"}],"
                + "\"collections\":[{\"displayName\":\"last\",\"name\":\"c\"}]}", json(builder.toBulkUpsert()));
    }

    @Test
    void objectsAreKeyedWithinTheirScope() {
        builder.upsertPool(new PoolInput().name("p").address("0x1"));
        builder.upsertPool(new PoolInput().name("p").address("0x2"));
        builder.upsertPool(new PoolInput().name("p").address("0x1").standard("ERC20"));
        builder.upsertNft(new NftInput().name("n").address("0x1"));
        builder.upsertNft(new NftInput().name("n"));
        builder.upsertFragment(new FragmentInput().name("f").address("0x1"));
        builder.upsertFragment(new FragmentInput().name("f").address("0x2"));
        builder.upsertEvent(new EventInput().name("e").activity("a1"));
        builder.upsertEvent(new EventInput().name("e").activity("a2"));
        builder.upsertEvent(new EventInput().name("e").activity("a1").displayName("E"));
        builder.upsertActivity(new ActivityInput().name("act"));
        builder.upsertActivity(new ActivityInput().name("act"));
        builder.upsertData(new DataInput().name("d"));
        builder.upsertData(new DataInput().name("d"));

        var items = builder.toBulkUpsert().items();
        assertEquals(2, items.get("pools").size());
        assertEquals(2, items.get("nfts").size());
        assertEquals(2, items.get("fragments").size());
        assertEquals(2, items.get("events").size());
        assertEquals(1, items.get("activities").size());
        assertEquals(1, items.get("data").size());
        assertEquals(10, builder.count());
    }

    @Test
    void objectsWithoutAKeyAreNeverMerged() {
        builder.upsertAsset(new AssetInput().displayName("one"));
        builder.upsertAsset(new AssetInput().displayName("two"));

        assertEquals(2, builder.count());
    }

    // ── Execute ─────────────────────────────────────────────────────────────

    @Test
    void executeSendsOneUpsertThenRunsFinalizersInOrderAndClears() {
        var ran = new ArrayList<String>();
        builder.upsertAsset(new AssetInput().name("bond").updateType(CREATE_OR_IGNORE))
                .upsertAddress(new AddressInput().address("0x1"))
                .addFinalizer(() -> ran.add("first"))
                .addFinalizer(() -> ran.add("second"));

        builder.execute();

        assertEquals(List.of("PUT /bulk/datamodel {\"addresses\":[{\"address\":\"0x1\"}],"
                + "\"assets\":[{\"name\":\"bond\",\"updateType\":\"create_or_ignore\"}]}"), am.sent());
        assertEquals(List.of("first", "second"), ran);
        assertFalse(builder.hasUpdates());
        assertEquals(0, builder.count());

        builder.execute();
        assertEquals(1, am.requests.size(), "an empty builder sends nothing");
        assertEquals(2, ran.size(), "finalizers run once");
    }

    @Test
    void finalizersRunWithoutUpdatesButNotAfterAFailure() {
        var ran = new ArrayList<String>();
        builder.addFinalizer(() -> ran.add("empty")).execute();
        assertEquals(List.of("empty"), ran);
        assertTrue(am.requests.isEmpty());

        am.answer = request -> status(500, "boom");
        builder.upsertAsset(new AssetInput().name("bond")).addFinalizer(() -> ran.add("failed"));
        assertThrows(ServiceClientException.class, builder::execute);

        assertEquals(List.of("empty"), ran);
        assertFalse(builder.hasUpdates(), "cleared after a failure too");
    }

    // ── Invalid references ──────────────────────────────────────────────────

    /** Rejects an upsert holding a pool whose asset neither exists nor is in an earlier upsert. */
    private void rejectPoolsOfMissingAssets(Set<String> existing) {
        am.answer = request -> {
            var body = request.body();
            for (var pool : body.path("pools")) {
                if (!existing.contains(pool.path("asset").asText())) {
                    return status(400, "{\"error\":\"KA090801: Invalid reference to asset '"
                            + pool.path("asset").asText() + "'\"}");
                }
            }
            body.path("assets").forEach(asset -> existing.add(asset.path("name").asText()));
            return ok("{}");
        };
    }

    @Test
    void anInvalidReferenceIsRetriedOneByOneUntilEverythingIsWritten() {
        rejectPoolsOfMissingAssets(new HashSet<>());
        var ran = new ArrayList<String>();
        builder.upsertPool(new PoolInput().name("p").asset("bond"))
                .upsertAsset(new AssetInput().name("bond"))
                .addFinalizer(() -> ran.add("done"));

        builder.execute();

        assertEquals(List.of(
                "PUT /bulk/datamodel {\"assets\":[{\"name\":\"bond\"}],\"pools\":[{\"asset\":\"bond\",\"name\":\"p\"}]}",
                "PUT /bulk/datamodel {\"pools\":[{\"asset\":\"bond\",\"name\":\"p\"}]}",
                "PUT /bulk/datamodel {\"assets\":[{\"name\":\"bond\"}]}",
                "PUT /bulk/datamodel {\"pools\":[{\"asset\":\"bond\",\"name\":\"p\"}]}"), am.sent());
        assertEquals(List.of("done"), ran);
    }

    @Test
    void objectsThatCanNeverBeWrittenAreReported() {
        rejectPoolsOfMissingAssets(new HashSet<>());
        builder.upsertPool(new PoolInput().name("p").asset("missing")).upsertAsset(new AssetInput().name("bond"));

        var error = assertThrows(BulkUpsertInvalidRefException.class, builder::execute);

        assertEquals("{\"pools\":[{\"asset\":\"missing\",\"name\":\"p\"}]}", json(error.stuck()));
        assertTrue(error.getMessage().contains("1 object(s)"), error.getMessage());
        assertTrue(BulkUpsertBuilder.isInvalidReference((ServiceClientException) error.getCause()));
        assertFalse(builder.hasUpdates());
    }

    @Test
    void withoutRetriesAnInvalidReferenceThrowsAtOnce() {
        rejectPoolsOfMissingAssets(new HashSet<>());
        builder.retryOnInvalidRef(false).upsertPool(new PoolInput().name("p").asset("bond"));

        var error = assertThrows(ServiceClientException.class, builder::execute);
        assertEquals(400, error.status());
        assertEquals(1, am.requests.size());
    }

    @Test
    void anotherErrorWhileRetryingStopsTheRetries() {
        am.answer = request -> am.requests.size() == 1
                ? status(400, "{\"error\":\"KA090801: Invalid reference\"}")
                : status(503, "unavailable");
        builder.upsertAsset(new AssetInput().name("a")).upsertAsset(new AssetInput().name("b"));

        var error = assertThrows(ServiceClientException.class, builder::execute);
        assertEquals(503, error.status());
        assertEquals(2, am.requests.size());
    }

    @Test
    void anInvalidReferenceIsRecognisedByItsCode() {
        assertTrue(BulkUpsertBuilder.isInvalidReference(
                new ServiceClientException("PUT /x: HTTP 400: KA090801 bad ref", 400, null, null)));
        assertTrue(BulkUpsertBuilder.isInvalidReference(
                new ServiceClientException("PUT /x: HTTP 400", 400, "{\"error\":\"KA090801\"}", null)));
        assertFalse(BulkUpsertBuilder.isInvalidReference(new ServiceClientException(null, 400, "KA000000", null)));
    }
}
