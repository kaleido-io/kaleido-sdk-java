// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.config;

import java.util.Base64;
import java.util.Map;

public sealed interface AuthConfig {

    String resolveHeaderName();
    String resolveHeaderValue();

    record BasicAuth(String username, String password) implements AuthConfig {
        @Override public String resolveHeaderName() { return "Authorization"; }
        @Override public String resolveHeaderValue() {
            return "Basic " + Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes());
        }
    }

    record TokenAuth(String token, String headerName, String scheme) implements AuthConfig {
        @Override public String resolveHeaderName() {
            return headerName != null ? headerName : "Authorization";
        }
        @Override public String resolveHeaderValue() {
            if (scheme != null && !scheme.isEmpty()) {
                return scheme + " " + token;
            }
            return token;
        }
    }

    record CustomHeaders(Map<String, String> headers) implements AuthConfig {
        @Override public String resolveHeaderName() { return null; }
        @Override public String resolveHeaderValue() { return null; }
    }
}
