// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.kaleido.workflowengine.sdk.protocol.WSMessageType;

import java.util.Map;

/**
 * Service proxy response received over WebSocket from the provider-proxy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceProxyResponse(
        WSMessageType messageType,
        String requestId,
        int status,
        Map<String, String> headers,
        String bodyBase64,
        String error
) {}
