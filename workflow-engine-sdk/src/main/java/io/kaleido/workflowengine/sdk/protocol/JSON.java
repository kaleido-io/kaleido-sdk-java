// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JSON {
    private JSON() {}

    /** The core SDK's mapper, so protocol messages and service calls serialize alike. */
    public static final ObjectMapper MAPPER = io.kaleido.sdk.core.JSON.MAPPER;
}
