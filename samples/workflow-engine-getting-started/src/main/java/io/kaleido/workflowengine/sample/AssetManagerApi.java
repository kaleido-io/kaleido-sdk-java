// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kaleido.sdk.core.http.ServiceClient;
import io.kaleido.sdk.core.http.ServiceClientOptions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A typed client for part of the Asset Manager's REST API, built on {@link ServiceClient}. It
 * works the same whichever way it gets its options: from a config file outside a provider, or
 * from a connected {@code WorkflowEngineClient} inside one.
 */
public class AssetManagerApi extends ServiceClient {

    /** Creates the object, or leaves an existing one as it is. */
    public static final String CREATE_OR_IGNORE = "create_or_ignore";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AssetInput(String name, String displayName, Map<String, String> labels, String updateType) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AddressInput(String address, Boolean contract, String updateType) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PoolInput(String name, String asset, String address, String standard, String displayName,
            Map<String, String> labels, String updateType) {
    }

    /** One bulk upsert: every list is optional. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BulkUpsert(List<AssetInput> assets, List<AddressInput> addresses, List<PoolInput> pools) {
    }

    public record NameAndId(String name, String id) {
    }

    /** What happened to each object of one type. */
    public record UpsertResult(List<NameAndId> created, List<NameAndId> replaced, List<NameAndId> updated,
            List<NameAndId> ignored) {
    }

    public record BulkUpsertResult(UpsertResult assets, UpsertResult addresses, UpsertResult pools) {
    }

    public record Asset(String id, String name, String displayName) {
    }

    public AssetManagerApi(ServiceClientOptions options) {
        super(options);
    }

    /** Creates or updates assets, addresses and pools in one request. */
    public BulkUpsertResult bulkUpsert(BulkUpsert input) {
        return put("/api/v1/bulk/datamodel", input, BulkUpsertResult.class);
    }

    /** The asset with this name or id, or empty when there is none. */
    public Optional<Asset> asset(String nameOrId) {
        var segment = URLEncoder.encode(nameOrId, StandardCharsets.UTF_8).replace("+", "%20");
        return find("/api/v1/assets/" + segment, Map.of(), Asset.class);
    }
}
