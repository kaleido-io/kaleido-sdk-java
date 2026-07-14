// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import io.kaleido.workflowengine.sdk.errors.SDKErrors;
import io.kaleido.workflowengine.sdk.handlers.EngineAPI;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.protocol.AsyncTransactionInput;
import io.kaleido.workflowengine.sdk.protocol.IdempotentSubmitResult;
import io.kaleido.workflowengine.sdk.protocol.WSEngineAPISubmitTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSEngineAPISubmitTransactionsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Client for handlers to call back to the workflow engine. Implements the
 * {@link EngineAPI} round-trip over the runtime's WebSocket connection.
 */
public class EngineClient implements EngineAPI {

    private static final Logger log = LoggerFactory.getLogger(EngineClient.class);

    private final HandlerRuntime runtime;
    private final ConcurrentHashMap<String, CompletableFuture<WSEngineAPISubmitTransactionsResult>> inflightRequests =
            new ConcurrentHashMap<>();

    EngineClient(HandlerRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public List<IdempotentSubmitResult> submitAsyncTransactions(
            RequestContext reqContext,
            String authRef,
            List<AsyncTransactionInput> transactions) throws Exception {

        if (!runtime.isWebSocketConnected()) {
            throw SDKErrors.newError(SDKErrors.MSG_ENGINE_NOT_CONNECTED);
        }
        reqContext.signal().throwIfCancelled();

        var requestId = UUID.randomUUID().toString();
        log.debug("Submitting async transactions requestId={} authRef={} count={}",
                requestId, authRef, transactions.size());

        var request = WSEngineAPISubmitTransactions.of(
                requestId, reqContext.requestId(), authRef, transactions);

        var future = new CompletableFuture<WSEngineAPISubmitTransactionsResult>();
        inflightRequests.put(requestId, future);
        try {
            runtime.sendMessage(request);
            var result = future.get(runtime.resultTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return result.submissions() != null ? result.submissions() : List.of();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        } finally {
            inflightRequests.remove(requestId);
        }
    }

    /**
     * Handle a response for an inflight request. Called by the runtime's
     * message loop.
     */
    void handleResponse(WSEngineAPISubmitTransactionsResult message) {
        var inflight = inflightRequests.remove(message.id());
        if (inflight == null) {
            log.warn("Received response for unknown request: {}", message.id());
            return;
        }
        if (message.error() != null) {
            log.error("EngineAPI request failed id={} error={}", message.id(), message.error());
            inflight.completeExceptionally(new RuntimeException(message.error()));
        } else {
            log.debug("EngineAPI request succeeded id={} results={}",
                    message.id(), message.submissions() != null ? message.submissions().size() : 0);
            inflight.complete(message);
        }
    }

    /**
     * Fail all in-flight requests (e.g. on disconnect).
     */
    void cancelAll() {
        var pending = inflightRequests.values().toArray(new CompletableFuture[0]);
        inflightRequests.clear();
        for (var future : pending) {
            future.completeExceptionally(SDKErrors.newError(SDKErrors.MSG_ENGINE_NOT_CONNECTED));
        }
    }
}
