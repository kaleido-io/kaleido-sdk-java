// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

/**
 * A {@link ServiceClient} call failed: no response, or a status other than 2xx.
 */
public class ServiceClientException extends RuntimeException {

    private final int status;
    private final String body;

    public ServiceClientException(String message, int status, String body, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.body = body;
    }

    /** The response status, or 0 when there was no response. */
    public int status() {
        return status;
    }

    /** The response body, or null when there was none. */
    public String body() {
        return body;
    }

    /**
     * Whether trying again could succeed: no response, 429, or 5xx. A handler can map this
     * to a transient error, and anything else to a hard failure.
     */
    public boolean retryable() {
        return status == 0 || status == 429 || status >= 500;
    }
}
