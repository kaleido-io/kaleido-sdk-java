// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Base64;
import java.util.Map;

/**
 * Sends requests for a hosted binding as service proxy messages over the provider's
 * WebSocket. The provider-proxy makes the call, as the user behind the request's
 * {@code authRef}.
 */
final class WsProxyServiceTransport implements ServiceTransport {

    private final ServiceClientOptions.WsProxy options;

    WsProxyServiceTransport(ServiceClientOptions.WsProxy options) {
        this.options = options;
    }

    @Override
    public ServiceResponse send(String method, String path, Object body) {
        var headers = body == null ? Map.<String, String>of() : Map.of("Content-Type", "application/json");
        try {
            var response = options.wsProxy().request(options.serviceType(), method, options.id(), body, headers, path,
                    options.authRef());
            var bytes = response.bodyBase64() == null ? new byte[0] : Base64.getDecoder().decode(response.bodyBase64());
            return new ServiceResponse(response.status(), response.headers() == null ? Map.of() : response.headers(),
                    bytes);
        } catch (ServiceProxyException e) {
            throw new ServiceClientException(method + " " + path + ": " + e.getMessage(), e.status(), null, e);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("body cannot be written as JSON: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceClientException(method + " " + path + " interrupted", 0, null, e);
        } catch (Exception e) {
            // Not connected, timed out, or the connection closed: the call may succeed if retried.
            throw new ServiceClientException(method + " " + path + " via the provider-proxy failed: " + e.getMessage(),
                    0, null, e);
        }
    }
}
