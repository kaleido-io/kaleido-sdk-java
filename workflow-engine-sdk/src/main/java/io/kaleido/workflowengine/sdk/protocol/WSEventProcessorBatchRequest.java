// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEventProcessorBatchRequest(
        WSMessageType messageType,
        String id,
        String deadline,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        String streamName,
        String streamId,
        List<ListenerEvent> events,
        String authRef
) implements WSHandlerEnvelope {}
