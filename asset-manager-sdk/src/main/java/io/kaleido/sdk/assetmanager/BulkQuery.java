// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Queries for several data-model types, answered in one request. */
public final class BulkQuery {

    private final Map<String, DataModelQuery> queries = new LinkedHashMap<>();

    public BulkQuery activities(DataModelQuery query) {
        return put("activities", query);
    }

    public BulkQuery addresses(DataModelQuery query) {
        return put("addresses", query);
    }

    public BulkQuery assets(DataModelQuery query) {
        return put("assets", query);
    }

    public BulkQuery collections(DataModelQuery query) {
        return put("collections", query);
    }

    public BulkQuery data(DataModelQuery query) {
        return put("data", query);
    }

    public BulkQuery events(DataModelQuery query) {
        return put("events", query);
    }

    public BulkQuery fragments(DataModelQuery query) {
        return put("fragments", query);
    }

    public BulkQuery nfts(DataModelQuery query) {
        return put("nfts", query);
    }

    public BulkQuery pools(DataModelQuery query) {
        return put("pools", query);
    }

    public BulkQuery transfers(DataModelQuery query) {
        return put("transfers", query);
    }

    public BulkQuery balanceChanges(DataModelQuery query) {
        return put("balanceChanges", query);
    }

    private BulkQuery put(String type, DataModelQuery query) {
        queries.put(type, query);
        return this;
    }

    /** The queries by type, as they are sent. */
    @JsonValue
    public Map<String, DataModelQuery> queries() {
        return Collections.unmodifiableMap(queries);
    }
}
