// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import io.kaleido.workflowengine.sdk.errors.SDKException;
import io.kaleido.workflowengine.sdk.protocol.WSEnvelope;
import io.kaleido.workflowengine.sdk.protocol.WSMessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RequestContextTest {

    private final CapturingHandlerRuntime runtime = new CapturingHandlerRuntime();

    private WSEnvelope envelope(String deadline) {
        return new WSEnvelope(WSMessageType.HANDLE_TRANSACTIONS, "req-1", deadline,
                null, "handler-1", null, Map.of("token-a", "value-a"), null);
    }

    @Test
    void carriesEnvelopeFields() {
        var context = runtime.newRequestContext(envelope(null));
        assertEquals("req-1", context.requestId());
        assertEquals("value-a", context.authTokens().get("token-a"));
        assertFalse(context.signal().isCancelled());
        context.cancel();
        assertTrue(context.signal().isCancelled());
    }

    @Test
    void deadlineCancelsSignal() throws Exception {
        var deadline = Instant.now().plusMillis(100).toString();
        var context = runtime.newRequestContext(envelope(deadline));

        var cancelled = new CountDownLatch(1);
        context.signal().onCancel(cancelled::countDown);

        assertFalse(context.signal().isCancelled());
        assertTrue(cancelled.await(5, TimeUnit.SECONDS));
        assertTrue(context.signal().isCancelled());
        assertTrue(context.signal().reason().contains("deadline exceeded"));
        assertThrows(SDKException.class, () -> context.signal().throwIfCancelled());
    }

    @Test
    void cancelReleasesDeadlineTimer() throws Exception {
        var deadline = Instant.now().plusMillis(100).toString();
        var context = runtime.newRequestContext(envelope(deadline));

        context.cancel();
        assertTrue(context.signal().isCancelled());
        // no reason: cancelled by completion, not by the deadline
        assertNull(context.signal().reason());
        Thread.sleep(200);
        assertNull(context.signal().reason());
    }

    @Test
    void unparseableDeadlineIgnored() {
        var context = runtime.newRequestContext(envelope("not-a-timestamp"));
        assertFalse(context.signal().isCancelled());
    }

    @Test
    void onCancelRunsImmediatelyWhenAlreadyCancelled() {
        var context = runtime.newRequestContext(envelope(null));
        context.cancel();
        var ran = new boolean[1];
        context.signal().onCancel(() -> ran[0] = true);
        assertTrue(ran[0]);
    }
}
