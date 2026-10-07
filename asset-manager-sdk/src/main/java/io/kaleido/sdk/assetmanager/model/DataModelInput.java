// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Fields every data-model input shares. Inputs are built fluently, and fields left unset are
 * not sent, so the same input serves a create, a partial update or a bulk upsert.
 *
 * @param <T> the input type, so each setter returns it
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class DataModelInput<T extends DataModelInput<T>> {

    private String displayName;
    private String description;
    private Object info;
    private Map<String, String> labels;
    private UpdateType updateType;

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    public T displayName(String displayName) {
        this.displayName = displayName;
        return self();
    }

    public T description(String description) {
        this.description = description;
        return self();
    }

    /** Free-form information, written as JSON: a map, a record or a {@code JsonNode}. */
    public T info(Object info) {
        this.info = info;
        return self();
    }

    public T labels(Map<String, String> labels) {
        this.labels = labels;
        return self();
    }

    /** How a bulk upsert treats an existing object; ignored outside a bulk upsert. */
    public T updateType(UpdateType updateType) {
        this.updateType = updateType;
        return self();
    }
}
