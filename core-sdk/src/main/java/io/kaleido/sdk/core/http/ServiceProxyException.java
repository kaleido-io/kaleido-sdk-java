// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

/**
 * The provider-proxy answered a service proxy request with an error: a 4xx or 5xx
 * status, or no status at all when it could not make the call.
 */
public class ServiceProxyException extends RuntimeException {

    private final int status;

    public ServiceProxyException(int status, String error) {
        super("Service proxy error: " + error);
        this.status = status;
    }

    /** The HTTP status the proxy reported, or 0 when it made no call. */
    public int status() {
        return status;
    }
}
