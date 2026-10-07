// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample;

import io.kaleido.sdk.assetmanager.AssetManagerClient;
import io.kaleido.sdk.assetmanager.model.AddressInput;
import io.kaleido.sdk.assetmanager.model.AssetInput;
import io.kaleido.sdk.assetmanager.model.PoolInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.kaleido.sdk.assetmanager.model.UpdateType.CREATE_OR_IGNORE;

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
        var am = AssetManagerClient.fromConfig();
        var labels = Map.of("demo", "true");

        am.newBulkUpsertBuilder()
                .upsertAsset(new AssetInput().name(ASSET).displayName("Getting Started Demo Asset")
                        .labels(labels).updateType(CREATE_OR_IGNORE))
                .upsertAddress(new AddressInput().address(POOL_ADDRESS).contract(true).updateType(CREATE_OR_IGNORE))
                .upsertPool(new PoolInput().name(POOL).asset(ASSET).address(POOL_ADDRESS).standard("ERC20")
                        .displayName("Getting Started Demo Pool").labels(labels).updateType(CREATE_OR_IGNORE))
                .addFinalizer(() -> log.info("Demo asset, address and pool are ready"))
                .execute();

        var asset = am.getAsset(ASSET).orElseThrow(() -> new IllegalStateException("Asset " + ASSET + " not found"));
        log.info("Asset {} has id {}", asset.name(), asset.id());
    }
}
