// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import java.util.Map;

/**
 * Sends a request for a hosted binding to the provider-proxy, which makes the call. The
 * workflow engine SDK's {@code WSProxyAdapter} implements this over the provider's WebSocket.
 */
public interface ServiceProxy {

    /**
     * Sends the request and waits for the proxy's answer.
     *
     * @param serviceType the platform service type, e.g. {@code AssetManagerService}
     * @param id          the service instance to call
     * @param authRef     the request's auth reference, so the call runs as its user, or null
     * @param method      the HTTP method
     * @param path        the path and query, relative to the service's base URL
     * @param headers     the request headers
     * @param body        the JSON body, or null for none
     * @return the service's response
     * @throws ServiceProxyException when the proxy answers with an error
     * @throws Exception             when the request could not be sent or was not answered
     */
    ServiceResponse send(String serviceType, String id, String authRef, String method, String path,
            Map<String, String> headers, Object body) throws Exception;
}
