// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.protocol.WSEventStreamInfo;

/**
 * Custom parser for event source stream config.
 */
@FunctionalInterface
public interface EventSourceConfigParserFn<CF> {
    CF parse(WSEventStreamInfo info, JsonNode config) throws Exception;
}
