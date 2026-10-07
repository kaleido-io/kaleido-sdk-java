// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kaleido.sdk.core.JSON;

/**
 * A query for one data-model type in a {@link BulkQuery}: filters, which all must match unless
 * combined with {@link #or}, then paging and sorting.
 *
 * <pre>{@code
 * DataModelQuery.create().eq("asset", "my-asset").gt("amount", "100").sort("-created").limit(50)
 * }</pre>
 */
public final class DataModelQuery {

    private final ObjectNode query = JSON.MAPPER.createObjectNode();

    private DataModelQuery() {
    }

    public static DataModelQuery create() {
        return new DataModelQuery();
    }

    public DataModelQuery eq(String field, String value) {
        return where("eq", field, value);
    }

    public DataModelQuery neq(String field, String value) {
        return where("neq", field, value);
    }

    public DataModelQuery contains(String field, String value) {
        return where("contains", field, value);
    }

    public DataModelQuery startsWith(String field, String value) {
        return where("startsWith", field, value);
    }

    public DataModelQuery endsWith(String field, String value) {
        return where("endsWith", field, value);
    }

    public DataModelQuery lt(String field, String value) {
        return where("lt", field, value);
    }

    public DataModelQuery lte(String field, String value) {
        return where("lte", field, value);
    }

    public DataModelQuery gt(String field, String value) {
        return where("gt", field, value);
    }

    public DataModelQuery gte(String field, String value) {
        return where("gte", field, value);
    }

    /** Matches when {@code field} is one of {@code values}. */
    public DataModelQuery in(String field, String... values) {
        return whereAny("in", field, values);
    }

    /** Matches when {@code field} is none of {@code values}. */
    public DataModelQuery notIn(String field, String... values) {
        return whereAny("nin", field, values);
    }

    /** Matches when {@code field} is not set. */
    public DataModelQuery isNull(String field) {
        query.withArray("null").addObject().put("field", field);
        return this;
    }

    /** Matches when the object has label {@code key} set to {@code value}. */
    public DataModelQuery label(String key, String value) {
        query.withObject("labels").withArray("eq").addObject().put("field", key).put("value", value);
        return this;
    }

    /** Matches when any of {@code alternatives} does. */
    public DataModelQuery or(DataModelQuery... alternatives) {
        var or = query.withArray("or");
        for (var alternative : alternatives) {
            or.add(alternative.query.deepCopy());
        }
        return this;
    }

    /**
     * Adds any filter the shortcuts do not cover.
     *
     * @param operator        the filter operator, e.g. {@code eq} or {@code startsWith}
     * @param field           the field
     * @param value           the value
     * @param not             whether to negate it
     * @param caseInsensitive whether to compare ignoring case
     * @return this query
     */
    public DataModelQuery where(String operator, String field, String value, boolean not, boolean caseInsensitive) {
        var term = query.withArray(operator).addObject().put("field", field).put("value", value);
        if (not) {
            term.put("not", true);
        }
        if (caseInsensitive) {
            term.put("caseInsensitive", true);
        }
        return this;
    }

    private DataModelQuery where(String operator, String field, String value) {
        return where(operator, field, value, false, false);
    }

    private DataModelQuery whereAny(String operator, String field, String... values) {
        ArrayNode list = query.withArray(operator).addObject().put("field", field).putArray("values");
        for (var value : values) {
            list.add(value);
        }
        return this;
    }

    public DataModelQuery skip(int skip) {
        query.put("skip", skip);
        return this;
    }

    public DataModelQuery limit(int limit) {
        query.put("limit", limit);
        return this;
    }

    /** Sort fields, a leading {@code -} for descending. */
    public DataModelQuery sort(String... fields) {
        var sort = query.putArray("sort");
        for (var field : fields) {
            sort.add(field);
        }
        return this;
    }

    /** Whether to return the total number of matches too. */
    public DataModelQuery count(boolean count) {
        query.put("count", count);
        return this;
    }

    /** Only these fields of each item. */
    public DataModelQuery fields(String... fields) {
        var list = query.putArray("fields");
        for (var field : fields) {
            list.add(field);
        }
        return this;
    }

    /** The query as it is sent. */
    @JsonValue
    public ObjectNode toJson() {
        return query.deepCopy();
    }

    @Override
    public String toString() {
        return query.toString();
    }
}
