// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

/**
 * Sends one HTTP-style request to a service. Created by {@link ServiceClient} from
 * {@link ServiceClientOptions}; implementations are not part of the API.
 */
interface ServiceTransport {

    /**
     * Sends the request and returns the response, whatever its status.
     *
     * @param method the HTTP method
     * @param path   the path and query, relative to the service's base URL
     * @param body   the JSON body, or null for none
     * @return the response
     */
    ServiceResponse send(String method, String path, Object body);
}
