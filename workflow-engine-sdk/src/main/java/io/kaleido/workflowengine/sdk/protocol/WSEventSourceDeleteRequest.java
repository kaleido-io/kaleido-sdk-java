// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Event source delete request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEventSourceDeleteRequest(
        WSMessageType messageType,
        String id,
        String deadline,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        String streamName,
        String streamId
) implements WSHandlerEnvelope {}
