// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceClientExceptionTest {

    private static ServiceClientException status(int status) {
        return new ServiceClientException("failed", status, "body", null);
    }

    @Test
    void noResponseTooManyRequestsAndServerErrorsAreRetryable() {
        for (var code : List.of(0, 429, 500, 502, 503, 599)) {
            assertTrue(status(code).retryable(), "status " + code);
        }
        for (var code : List.of(400, 401, 403, 404, 409, 428, 430, 499)) {
            assertFalse(status(code).retryable(), "status " + code);
        }
    }

    @Test
    void itKeepsTheStatusBodyAndCause() {
        var cause = new IllegalStateException("x");
        var error = new ServiceClientException("GET /a failed", 0, null, cause);

        assertEquals("GET /a failed", error.getMessage());
        assertEquals(0, error.status());
        assertNull(error.body());
        assertSame(cause, error.getCause());
        assertEquals("body", status(500).body());
    }

    @Test
    void aProxyExceptionKeepsItsStatus() {
        var error = new ServiceProxyException(502, "upstream down");

        assertEquals(502, error.status());
        assertEquals("Service proxy error: upstream down", error.getMessage());
    }
}
