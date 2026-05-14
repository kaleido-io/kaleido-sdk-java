// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.dispatch;

import io.kaleido.wfe.sdk.errors.SDKErrors;
import io.kaleido.wfe.sdk.handlers.*;
import io.kaleido.wfe.sdk.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Routes inbound WebSocket messages to the right {@link TransactionHandler},
 * {@link EventProcessor}, or {@link EventSource}. Shared by both outbound and
 * server mode so dispatch logic is never duplicated.
 *
 * <p>Invariants (ported from {@code enginesdk.handlerRuntime} -- see
 * {@code wsReceiveLoop} / {@code asyncHandleWithResponse} in
 * {@code workflow-engine/pkg/enginesdk/handler_runtime.go} and
 * {@code .cursor/plans/go-sdk.md}):
 * <ol>
 *   <li>{@link WSMessageType#EVENT_SOURCE_CONFIG} runs synchronously before any
 *       subsequent {@link WSMessageType#EVENT_SOURCE_POLL} so the poll sees the
 *       new config. All other handler invocations may run on the executor.</li>
 *   <li>Every handler invocation is wrapped so that on exception a same-id
 *       result envelope is emitted with {@code error} set -- the engine's
 *       {@code roundTrip} caller will otherwise time out at 2 minutes.</li>
 *   <li>{@link TransactionHandler#handleTransactionBatch} must return a result list
 *       with the same length as the request's transactions; the dispatcher
 *       rejects mismatched batches with an error response.</li>
 * </ol>
 */
public class WFEDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WFEDispatcher.class);

    private final Map<String, Handler> handlers;
    private final AtomicReference<String> activeRequestId;

    public WFEDispatcher(Map<String, Handler> handlers, AtomicReference<String> activeRequestId) {
        this.handlers = handlers;
        this.activeRequestId = activeRequestId;
    }

    /**
     * Sends the post-upgrade registration handshake on a freshly-opened WebSocket
     * (provider-side, regardless of who dialed): {@link WSRegisterProvider} first,
     * then one {@link WSRegisterHandler} per handler with its derived type.
     * Mirrors {@code wsRegisterAndStart} in the Go SDK.
     */
    public static void sendRegistration(
            String providerName,
            com.fasterxml.jackson.databind.JsonNode providerMetadata,
            Map<String, Handler> handlers,
            Consumer<Object> sender) {
        sender.accept(WSRegisterProvider.of(providerName, providerMetadata));
        for (var handler : handlers.values()) {
            WSHandlerType type = typeFor(handler);
            if (type == null) continue;
            sender.accept(WSRegisterHandler.of(handler.name(), type));
        }
    }

    private static WSHandlerType typeFor(Handler handler) {
        if (handler instanceof TransactionHandler) return WSHandlerType.TRANSACTION_HANDLER;
        if (handler instanceof EventProcessor) return WSHandlerType.EVENT_PROCESSOR;
        if (handler instanceof EventSource) return WSHandlerType.EVENT_SOURCE;
        return null;
    }

    /**
     * Dispatches a parsed envelope to the correct handler, sending the response
     * via the provided sender callback. Caller is responsible for choosing
     * threading: {@link WSMessageType#EVENT_SOURCE_CONFIG} must run on the
     * receive thread (synchronous), everything else may be offloaded.
     */
    public void dispatch(WSEnvelope envelope, String rawJson, Consumer<Object> sender) {
        if (envelope.messageType() == null) {
            log.warn("Received message with no messageType");
            return;
        }

        switch (envelope.messageType()) {
            case HANDLE_TRANSACTIONS -> respondSafely(envelope, sender,
                    WSMessageType.HANDLE_TRANSACTIONS_RESULT,
                    () -> JSON.MAPPER.readValue(rawJson, WSHandleTransactions.class),
                    this::invokeTransactionHandler);
            case EVENT_PROCESSOR_BATCH -> respondSafely(envelope, sender,
                    WSMessageType.EVENT_PROCESSOR_BATCH_RESULT,
                    () -> JSON.MAPPER.readValue(rawJson, WSEventProcessorBatchRequest.class),
                    this::invokeEventProcessor);
            case EVENT_SOURCE_CONFIG -> handleEventSourceConfig(rawJson);
            case EVENT_SOURCE_VALIDATE_CONFIG -> respondSafely(envelope, sender,
                    WSMessageType.EVENT_SOURCE_VALIDATE_CONFIG_RESULT,
                    () -> JSON.MAPPER.readValue(rawJson, WSEventSourceValidateConfig.class),
                    this::invokeValidateConfig);
            case EVENT_SOURCE_POLL -> respondSafely(envelope, sender,
                    WSMessageType.EVENT_SOURCE_POLL_RESULT,
                    () -> JSON.MAPPER.readValue(rawJson, WSEventSourcePoll.class),
                    this::invokePoll);
            case EVENT_SOURCE_DELETE -> respondSafely(envelope, sender,
                    WSMessageType.EVENT_SOURCE_DELETE_RESULT,
                    () -> JSON.MAPPER.readValue(rawJson, WSEventSourceDelete.class),
                    this::invokeDelete);
            case PROTOCOL_ERROR -> log.error("Protocol error from engine: {}", envelope.error());
            default -> log.debug("Unhandled message type: {}", envelope.messageType());
        }
    }

    private Object invokeTransactionHandler(WSHandleTransactions req) throws Exception {
        var handler = handlers.get(req.handler());
        if (!(handler instanceof TransactionHandler txnHandler)) {
            return WSHandleTransactionsResult.error(req, "Handler not found: " + req.handler());
        }
        activeRequestId.set(req.id());
        try {
            var result = txnHandler.handleTransactionBatch(req);
            int expected = req.transactions() == null ? 0 : req.transactions().size();
            int actual = result == null || result.results() == null ? 0 : result.results().size();
            if (result != null && result.error() == null && actual != expected) {
                log.error("Handler {} returned {} results for {} transactions",
                        req.handler(), actual, expected);
                throw SDKErrors.error(SDKErrors.HANDLER_FAILED,
                        "TransactionHandler must return one result per input transaction; got "
                                + actual + " for " + expected);
            }
            return result;
        } finally {
            activeRequestId.set(null);
        }
    }

    private Object invokeEventProcessor(WSEventProcessorBatchRequest req) throws Exception {
        var handler = handlers.get(req.handler());
        if (!(handler instanceof EventProcessor ep)) {
            return WSEventProcessorBatchResult.error(req, "Handler not found: " + req.handler());
        }
        activeRequestId.set(req.id());
        try {
            return ep.processEvents(req);
        } finally {
            activeRequestId.set(null);
        }
    }

    private Object invokeValidateConfig(WSEventSourceValidateConfig req) throws Exception {
        var handler = handlers.get(req.handler());
        if (!(handler instanceof EventSource es)) {
            return WSEventSourceValidateConfigResult.error(req, "Handler not found: " + req.handler());
        }
        return es.validateConfig(req);
    }

    private Object invokePoll(WSEventSourcePoll req) throws Exception {
        var handler = handlers.get(req.handler());
        if (!(handler instanceof EventSource es)) {
            return WSEventSourcePollResult.error(req, "Handler not found: " + req.handler());
        }
        return es.poll(req);
    }

    private Object invokeDelete(WSEventSourceDelete req) throws Exception {
        var handler = handlers.get(req.handler());
        if (!(handler instanceof EventSource es)) {
            return WSEventSourceDeleteResult.error(req, "Handler not found: " + req.handler());
        }
        return es.delete(req);
    }

    private void handleEventSourceConfig(String json) {
        try {
            var request = JSON.MAPPER.readValue(json, WSEventSourceConfig.class);
            var handler = handlers.get(request.handler());
            if (!(handler instanceof EventSource es)) {
                log.warn("EventSource config for unknown handler: {}", request.handler());
                return;
            }
            es.onConfigChanged(request.streamId());
        } catch (Throwable t) {
            log.error("Error processing event source config", t);
        }
    }

    /**
     * Always emits a same-id result envelope. If parsing fails we still synthesise
     * one with {@code error} set from the original envelope. Mirrors
     * {@code asyncHandleWithResponse} in the Go SDK.
     */
    private <REQ> void respondSafely(
            WSEnvelope envelope,
            Consumer<Object> sender,
            WSMessageType resultType,
            ThrowingSupplier<REQ> parser,
            ThrowingFunction<REQ, Object> invocation) {
        try {
            var request = parser.get();
            var result = invocation.apply(request);
            sender.accept(result);
        } catch (Throwable t) {
            log.error("Error processing {}: {}", resultType, t.getMessage(), t);
            try {
                sender.accept(buildErrorResult(resultType, envelope.id(), envelope.handler(), t.getMessage()));
            } catch (Throwable ex) {
                log.error("Failed to send error result for {}", resultType, ex);
            }
        }
    }

    private static Object buildErrorResult(WSMessageType resultType, String id, String handler, String error) {
        return switch (resultType) {
            case HANDLE_TRANSACTIONS_RESULT -> new WSHandleTransactionsResult(
                    resultType, id, null, handler, error, null, null);
            case EVENT_PROCESSOR_BATCH_RESULT -> new WSEventProcessorBatchResult(
                    resultType, id, null, handler, error, null, null, null);
            case EVENT_SOURCE_VALIDATE_CONFIG_RESULT -> new WSEventSourceValidateConfigResult(
                    resultType, id, null, handler, error, null, null);
            case EVENT_SOURCE_POLL_RESULT -> new WSEventSourcePollResult(
                    resultType, id, null, handler, error, null, null, null);
            case EVENT_SOURCE_DELETE_RESULT -> new WSEventSourceDeleteResult(
                    resultType, id, null, handler, error, null);
            default -> WSEnvelope.error(error);
        };
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }
}
