// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import java.util.Map;

/**
 * Common fields shared by handler-dispatch WebSocket messages.
 */
public interface WSHandlerEnvelope {
    WSMessageType messageType();
    String id();
    default String deadline() { return null; }
    default WSHandlerType handlerType() { return null; }
    String handler();
    default String error() { return null; }
    default Map<String, String> authTokens() { return null; }
    /** Auth reference forwarded from the WFE request, when present on the message. */
    default String authRef() { return null; }
}
