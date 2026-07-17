// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

/**
 * Interface satisfied by the handler runtime, allowing the adapter to send
 * messages over the runtime's existing WebSocket connection.
 *
 * <p>In hosted mode, this WebSocket points to the provider-proxy which
 * transparently forwards WFE protocol messages while intercepting
 * service-proxy requests for local HTTP execution.
 */
public interface ProxyAdapterRuntime {
    void sendMessage(Object message);
    boolean isWebSocketConnected();
}
