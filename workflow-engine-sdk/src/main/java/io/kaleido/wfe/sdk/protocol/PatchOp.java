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
        String from,
        String jsonata,
        String location
) {
    public static PatchOp add(String path, JsonNode value) {
        return new PatchOp(PatchOpType.ADD, path, value, null, null, null);
    }

    public static PatchOp replace(String path, JsonNode value) {
        return new PatchOp(PatchOpType.REPLACE, path, value, null, null, null);
    }

    public static PatchOp remove(String path) {
        return new PatchOp(PatchOpType.REMOVE, path, null, null, null, null);
    }

    public static PatchOp jsonata(String path, String expression) {
        return new PatchOp(PatchOpType.JSONATA, path, null, null, expression, null);
    }

    public static PatchOp jsonata(String path, String expression, String location) {
        return new PatchOp(PatchOpType.JSONATA, path, null, null, expression, location);
    }
}
