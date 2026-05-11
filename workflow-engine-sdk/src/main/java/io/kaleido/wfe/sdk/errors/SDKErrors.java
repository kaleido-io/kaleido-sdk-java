// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.errors;

public final class SDKErrors {
    private SDKErrors() {}

    public static final String ACCOUNT_NOT_SET = "KA150001";
    public static final String ENVIRONMENT_NOT_SET = "KA150002";
    public static final String WORKFLOW_ENGINE_NOT_SET = "KA150003";
    public static final String CONFIG_FILE_NOT_SET = "KA150004";
    public static final String CONFIG_FILE_READ_FAILED = "KA150005";
    public static final String WS_CONNECT_FAILED = "KA150010";
    public static final String WS_SEND_FAILED = "KA150011";
    public static final String WS_PROTOCOL_ERROR = "KA150012";
    public static final String HANDLER_NOT_FOUND = "KA150020";
    public static final String HANDLER_FAILED = "KA150021";
    public static final String ENGINE_API_FAILED = "KA150030";
    public static final String REST_REQUEST_FAILED = "KA150040";

    public static SDKException error(String code, String message) {
        return new SDKException(code, message);
    }

    public static SDKException error(String code, String message, Throwable cause) {
        return new SDKException(code, message, cause);
    }
}
