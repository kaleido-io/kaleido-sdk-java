// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum WSHandlerType {
    @JsonProperty("transaction_handler") TRANSACTION_HANDLER,
    @JsonProperty("event_source")        EVENT_SOURCE,
    @JsonProperty("event_processor")     EVENT_PROCESSOR;
}
