// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PatchOp(
        PatchOpType op,
        String path,
        JsonNode value,
        String from
) {
    public static PatchOp add(String path, JsonNode value) {
        return new PatchOp(PatchOpType.ADD, path, value, null);
    }

    public static PatchOp replace(String path, JsonNode value) {
        return new PatchOp(PatchOpType.REPLACE, path, value, null);
    }

    public static PatchOp remove(String path) {
        return new PatchOp(PatchOpType.REMOVE, path, null, null);
    }
}
