// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Authentication configuration for a non-hosted service binding.
 * {@code type} is "basic" (username/password) or "token" (token with optional
 * header name and scheme prefix).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceBindingAuth(
        String type,
        String username,
        String password,
        String token,
        String header,
        String scheme
) {
    public static ServiceBindingAuth basic(String username, String password) {
        return new ServiceBindingAuth("basic", username, password, null, null, null);
    }

    public static ServiceBindingAuth token(String token, String header, String scheme) {
        return new ServiceBindingAuth("token", null, null, token, header, scheme);
    }
}
