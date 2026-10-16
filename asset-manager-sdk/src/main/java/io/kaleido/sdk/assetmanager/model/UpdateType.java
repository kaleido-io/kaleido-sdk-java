// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** How a bulk upsert treats an object that may already exist. */
public enum UpdateType {
    /** Create it; fail if it exists. */
    @JsonProperty("create_only") CREATE_ONLY,
    /** Update it; fail if it does not exist. */
    @JsonProperty("update_only") UPDATE_ONLY,
    /** Create it, or replace the existing one. */
    @JsonProperty("create_or_replace") CREATE_OR_REPLACE,
    /** Create it, or merge into the existing one. */
    @JsonProperty("create_or_update") CREATE_OR_UPDATE,
    /** Create it, or leave the existing one as it is. */
    @JsonProperty("create_or_ignore") CREATE_OR_IGNORE
}
