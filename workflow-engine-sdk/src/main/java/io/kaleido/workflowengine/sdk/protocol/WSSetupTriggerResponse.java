// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Setup trigger response: the SDK's reply to a SETUP_TRIGGER_REQUEST.
 * {@code status} is "success" when all setup hooks completed; "error"
 * otherwise, with {@code errors} carrying one entry per failed hook.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSSetupTriggerResponse(
        WSMessageType messageType,
        String requestId,
        String status,
        List<String> errors
) {
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";

    public static WSSetupTriggerResponse success(String requestId) {
        return new WSSetupTriggerResponse(WSMessageType.SETUP_TRIGGER_RESPONSE, requestId, STATUS_SUCCESS, null);
    }

    public static WSSetupTriggerResponse error(String requestId, List<String> errors) {
        return new WSSetupTriggerResponse(WSMessageType.SETUP_TRIGGER_RESPONSE, requestId, STATUS_ERROR,
                errors == null || errors.isEmpty() ? null : errors);
    }
}
