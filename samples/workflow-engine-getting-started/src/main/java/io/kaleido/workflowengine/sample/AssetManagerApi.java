// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample;

import io.kaleido.sdk.core.http.ServiceClient;
import io.kaleido.sdk.core.http.ServiceClientOptions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * A typed client for the Asset Manager's REST API, built on {@link ServiceClient}. It works
 * the same whichever way it gets its options: from a config file outside a provider, or
 * from a connected {@code WorkflowEngineClient} inside one.
 */
public class AssetManagerApi extends ServiceClient {

    public record Status(String status) {
    }

    public record Asset(String id, String name) {
    }

    public AssetManagerApi(ServiceClientOptions options) {
        super(options);
    }

    /** The Asset Manager's status. */
    public Status status() {
        return get("/api/v1/status", Status.class);
    }

    /** The asset with this name or id, or empty when there is none. */
    public Optional<Asset> asset(String nameOrId) {
        var segment = URLEncoder.encode(nameOrId, StandardCharsets.UTF_8).replace("+", "%20");
        return find("/api/v1/assets/" + segment, Map.of(), Asset.class);
    }
}
