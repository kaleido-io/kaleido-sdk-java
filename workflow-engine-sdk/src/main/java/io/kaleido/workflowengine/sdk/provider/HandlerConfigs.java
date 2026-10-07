// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.provider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaleido.workflowengine.sdk.protocol.JSON;

/** Binds handler config: the SDK's mapper, but strict about unknown keys and missing primitives. */
final class HandlerConfigs {

    static final ObjectMapper MAPPER = JSON.MAPPER.copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    private HandlerConfigs() {
    }
}
