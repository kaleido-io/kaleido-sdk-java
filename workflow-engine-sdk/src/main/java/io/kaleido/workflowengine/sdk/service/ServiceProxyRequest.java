// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.kaleido.workflowengine.sdk.protocol.WSMessageType;

import java.util.Map;

/**
 * Service proxy request sent over WebSocket to the provider-proxy.
 *
 * <p>The {@code id} identifies the target service instance on the proxy side,
 * which maps to the actual service URL. The request contains only the HTTP
 * method, path, headers, and body — the proxy resolves the full URL from the
 * {@code id}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceProxyRequest(
        WSMessageType messageType,
        String requestId,
        String serviceType,
        String id,
        String authRef,
        String invocationId,
        HttpRequestSpec request
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HttpRequestSpec(
            String method,
            String path,
            Map<String, String> headers,
            Map<String, String> params,
            String bodyBase64
    ) {}
}
