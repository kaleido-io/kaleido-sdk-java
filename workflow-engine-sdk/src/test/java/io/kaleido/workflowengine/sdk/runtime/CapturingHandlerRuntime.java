// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import io.kaleido.workflowengine.sdk.config.ClientConfig;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test runtime that captures outbound messages instead of writing to a
 * WebSocket, and never connects.
 */
public class CapturingHandlerRuntime extends HandlerRuntime {

    public final BlockingQueue<Object> sentMessages = new LinkedBlockingQueue<>();
    private volatile boolean simulateConnected = true;

    public CapturingHandlerRuntime() {
        this(ClientConfig.builder().providerName("test-provider").build());
    }

    public CapturingHandlerRuntime(ClientConfig config) {
        super(config);
    }

    @Override
    public void start() {
        // never connects in tests
    }

    @Override
    public void sendMessage(Object message) {
        sentMessages.add(message);
    }

    @Override
    public boolean isWebSocketConnected() {
        return simulateConnected;
    }

    public void simulateDisconnected() {
        this.simulateConnected = false;
    }

    /** Route an incoming JSON message and wait for the response it produces. */
    public <T> T roundTrip(String json, Class<T> expectedResponse) throws Exception {
        handleMessage(json);
        var sent = sentMessages.poll(5, TimeUnit.SECONDS);
        if (sent == null) {
            throw new AssertionError("No response message sent within timeout");
        }
        return expectedResponse.cast(sent);
    }
}
