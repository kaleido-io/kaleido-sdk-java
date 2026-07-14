// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Listener poll request dispatched to an event source.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSListenerPollRequest(
        WSMessageType messageType,
        String id,
        String deadline,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        String streamName,
        String streamId,
        String listenerName,
        JsonNode checkpoint,
        String authRef
) implements WSHandlerEnvelope {}
