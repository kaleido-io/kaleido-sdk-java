// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.patch;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.PatchOp;

import java.util.List;

/**
 * RFC 6902 JSON Patch application over Jackson trees.
 */
public final class JsonPatch {
    private JsonPatch() {}

    /**
     * Apply a JSON Patch to a state document, returning the new state.
     * An empty or null patch returns the input state unchanged.
     */
    public static JsonNode apply(JsonNode state, List<PatchOp> patch) {
        if (patch == null || patch.isEmpty()) {
            return state;
        }
        JsonNode patchNode = JSON.MAPPER.valueToTree(patch);
        return com.flipkart.zjsonpatch.JsonPatch.apply(patchNode, state);
    }
}
