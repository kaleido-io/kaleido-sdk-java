// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PatchOpType {
    @JsonProperty("add")     ADD,
    @JsonProperty("remove")  REMOVE,
    @JsonProperty("replace") REPLACE,
    @JsonProperty("move")    MOVE,
    @JsonProperty("copy")    COPY,
    @JsonProperty("test")    TEST,
    @JsonProperty("jsonata") JSONATA;
}
