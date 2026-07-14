// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.handlers;

import io.kaleido.workflowengine.sdk.errors.SDKException;

import java.util.ArrayList;
import java.util.List;

/**
 * Cancellation signal carried by a {@link RequestContext}. Cancelled by the
 * runtime when the request deadline passes or the dispatch completes.
 */
public final class CancellationSignal {

    private final Object lock = new Object();
    private boolean cancelled;
    private String reason;
    private List<Runnable> callbacks = new ArrayList<>();

    /** True once the signal has been cancelled. */
    public boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

    /** The cancellation reason, or null if not cancelled (or no reason given). */
    public String reason() {
        synchronized (lock) {
            return reason;
        }
    }

    /**
     * Register a callback to run when the signal is cancelled. Runs immediately
     * if the signal is already cancelled.
     */
    public void onCancel(Runnable callback) {
        boolean runNow;
        synchronized (lock) {
            runNow = cancelled;
            if (!runNow) {
                callbacks.add(callback);
            }
        }
        if (runNow) {
            callback.run();
        }
    }

    /** Throw if the signal has been cancelled — port of {@code AbortSignal.throwIfAborted()}. */
    public void throwIfCancelled() {
        String cancelReason;
        synchronized (lock) {
            if (!cancelled) {
                return;
            }
            cancelReason = reason;
        }
        throw new SDKException("KA140641", "Request cancelled" + (cancelReason != null ? ": " + cancelReason : ""));
    }

    /** Cancel the signal, running any registered callbacks. */
    public void cancel(String reason) {
        List<Runnable> toRun;
        synchronized (lock) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            this.reason = reason;
            toRun = callbacks;
            callbacks = new ArrayList<>();
        }
        for (var callback : toRun) {
            callback.run();
        }
    }
}
