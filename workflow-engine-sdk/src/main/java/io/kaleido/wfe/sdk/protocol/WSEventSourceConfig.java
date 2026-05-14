// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Engine-to-SDK message that (re)initialises listener config for a stream.
 * The dispatcher must run the handler synchronously before any subsequent
 * {@link WSEventSourcePoll} so the poll sees the new config -- see
 * {@code wsReceiveLoop} in
 * {@code workflow-engine/pkg/enginesdk/handler_runtime.go} and
 * {@code .cursor/plans/go-sdk.md} for the protocol source of truth.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEventSourceConfig(
        WSMessageType messageType,
        String id,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        String streamName,
        String streamId,
        JsonNode config
) {}
