// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.errors;

import com.fasterxml.jackson.databind.JsonNode;

public class SDKException extends RuntimeException {
    private final String code;
    private final JsonNode data;

    public SDKException(String code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    public SDKException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.data = null;
    }

    public SDKException(String code, String message, JsonNode data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public String code() { return code; }
    public JsonNode data() { return data; }
}
