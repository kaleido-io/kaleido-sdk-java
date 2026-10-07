// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample;

import io.kaleido.sdk.core.http.ServiceClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls the Asset Manager through the {@code asset-manager} service binding in
 * {@code KALEIDO_CONFIG_FILE}, without a provider: logs its status and, given a name or id,
 * looks up that asset.
 */
public final class CheckAssetManager {

    private static final Logger log = LoggerFactory.getLogger(CheckAssetManager.class);

    public static void main(String[] args) {
        var assets = new AssetManagerApi(ServiceClientOptions.fromConfig("asset-manager"));

        log.info("Asset Manager status: {}", assets.status().status());
        if (args.length > 0 && !args[0].isBlank()) {
            assets.asset(args[0]).ifPresentOrElse(
                    asset -> log.info("Asset {}: id={}", asset.name(), asset.id()),
                    () -> log.info("No asset named {}", args[0]));
        }
    }
}
