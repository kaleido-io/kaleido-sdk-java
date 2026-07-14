// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON Patch operation following RFC 6902.
 */
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

    public static PatchOp add(String path, Object value) {
        return add(path, JSON.MAPPER.valueToTree(value));
    }

    public static PatchOp remove(String path) {
        return new PatchOp(PatchOpType.REMOVE, path, null, null);
    }

    public static PatchOp replace(String path, JsonNode value) {
        return new PatchOp(PatchOpType.REPLACE, path, value, null);
    }

    public static PatchOp replace(String path, Object value) {
        return replace(path, (JsonNode) JSON.MAPPER.valueToTree(value));
    }

    public static PatchOp move(String from, String path) {
        return new PatchOp(PatchOpType.MOVE, path, null, from);
    }

    public static PatchOp copy(String from, String path) {
        return new PatchOp(PatchOpType.COPY, path, null, from);
    }

    public static PatchOp test(String path, JsonNode value) {
        return new PatchOp(PatchOpType.TEST, path, value, null);
    }
}
