// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample;

import io.kaleido.sdk.core.http.ServiceClientOptions;
import io.kaleido.workflowengine.sample.AssetManagerApi.AddressInput;
import io.kaleido.workflowengine.sample.AssetManagerApi.AssetInput;
import io.kaleido.workflowengine.sample.AssetManagerApi.BulkUpsert;
import io.kaleido.workflowengine.sample.AssetManagerApi.PoolInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.kaleido.workflowengine.sample.AssetManagerApi.CREATE_OR_IGNORE;

/**
 * Makes sure a demo asset, its contract address and a pool exist in the Asset Manager, in one
 * bulk upsert, then reads the asset back. It calls the Asset Manager through the
 * {@code asset-manager} service binding in {@code KALEIDO_CONFIG_FILE}, without a provider.
 * Safe to run again: existing objects are left as they are.
 */
public final class SetupDemoAsset {

    private static final Logger log = LoggerFactory.getLogger(SetupDemoAsset.class);

    static final String ASSET = "getting-started-demo-asset";
    static final String POOL = "getting-started-demo-pool";
    static final String POOL_ADDRESS = "0x0000000000000000000000000000000000000001";

    public static void main(String[] args) {
        var assets = new AssetManagerApi(ServiceClientOptions.fromConfig("asset-manager"));
        var labels = Map.of("demo", "true");

        var result = assets.bulkUpsert(new BulkUpsert(
                List.of(new AssetInput(ASSET, "Getting Started Demo Asset", labels, CREATE_OR_IGNORE)),
                List.of(new AddressInput(POOL_ADDRESS, true, CREATE_OR_IGNORE)),
                List.of(new PoolInput(POOL, ASSET, POOL_ADDRESS, "ERC20", "Getting Started Demo Pool", labels,
                        CREATE_OR_IGNORE))));
        log.info("Bulk upsert: assets [{}], addresses [{}], pools [{}]",
                summary(result.assets()), summary(result.addresses()), summary(result.pools()));

        var asset = assets.asset(ASSET).orElseThrow(() -> new IllegalStateException("Asset " + ASSET + " not found"));
        log.info("Asset {} has id {}", asset.name(), asset.id());
    }

    private static String summary(AssetManagerApi.UpsertResult result) {
        return result == null ? "unchanged" : "created " + count(result.created()) + ", ignored "
                + count(result.ignored()) + ", updated " + (count(result.updated()) + count(result.replaced()));
    }

    private static int count(List<?> items) {
        return items == null ? 0 : items.size();
    }
}
