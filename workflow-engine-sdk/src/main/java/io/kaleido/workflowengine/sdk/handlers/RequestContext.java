// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.handlers;

import java.util.Map;

/**
 * Per-request context passed to every handler dispatch. The {@link #signal()}
 * is cancelled when the request's deadline passes, or when the runtime
 * finishes the dispatch.
 */
public final class RequestContext {

    private final String requestId;
    private final Map<String, String> authTokens;
    private final String authRef;
    private final CancellationSignal signal;
    private final Runnable onCancel;

    public RequestContext(String requestId, Map<String, String> authTokens, String authRef,
                          CancellationSignal signal, Runnable onCancel) {
        this.requestId = requestId;
        this.authTokens = authTokens;
        this.authRef = authRef;
        this.signal = signal;
        this.onCancel = onCancel;
    }

    public String requestId() {
        return requestId;
    }

    public Map<String, String> authTokens() {
        return authTokens;
    }

    /** Auth reference forwarded from the WFE request — used by ws-proxy transport to inject credentials. */
    public String authRef() {
        return authRef;
    }

    public CancellationSignal signal() {
        return signal;
    }

    /** Cancel the request context, releasing any deadline timer. */
    public void cancel() {
        if (onCancel != null) {
            onCancel.run();
        }
        signal.cancel(null);
    }

}
