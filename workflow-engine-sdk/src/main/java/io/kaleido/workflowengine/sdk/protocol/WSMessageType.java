// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum WSMessageType {
    @JsonProperty("protocol_error")                        PROTOCOL_ERROR,
    @JsonProperty("register_provider")                     REGISTER_PROVIDER,
    @JsonProperty("register_handler")                      REGISTER_HANDLER,
    @JsonProperty("evaluate")                              EVALUATE,
    @JsonProperty("evaluate_result")                       EVALUATE_RESULT,
    @JsonProperty("handle_transactions")                   HANDLE_TRANSACTIONS,
    @JsonProperty("handle_transactions_result")            HANDLE_TRANSACTIONS_RESULT,
    @JsonProperty("event_source_config")                   EVENT_SOURCE_CONFIG,
    @JsonProperty("event_source_poll")                     EVENT_SOURCE_POLL,
    @JsonProperty("event_source_poll_result")              EVENT_SOURCE_POLL_RESULT,
    @JsonProperty("event_source_validate_config")          EVENT_SOURCE_VALIDATE_CONFIG,
    @JsonProperty("event_source_validate_config_result")   EVENT_SOURCE_VALIDATE_CONFIG_RESULT,
    @JsonProperty("event_source_delete")                   EVENT_SOURCE_DELETE,
    @JsonProperty("event_source_delete_result")            EVENT_SOURCE_DELETE_RESULT,
    @JsonProperty("event_processor_batch")                 EVENT_PROCESSOR_BATCH,
    @JsonProperty("event_processor_batch_result")          EVENT_PROCESSOR_BATCH_RESULT,
    @JsonProperty("engineapi_submit_transactions")         ENGINE_API_SUBMIT_TRANSACTIONS,
    @JsonProperty("engineapi_submit_transactions_result")  ENGINE_API_SUBMIT_TRANSACTIONS_RESULT,
    @JsonProperty("service-proxy-request")                 SERVICE_PROXY_REQUEST,
    @JsonProperty("service-proxy-response")                SERVICE_PROXY_RESPONSE,
    @JsonProperty("setup-trigger-request")                 SETUP_TRIGGER_REQUEST,
    @JsonProperty("setup-trigger-response")                SETUP_TRIGGER_RESPONSE;
}
