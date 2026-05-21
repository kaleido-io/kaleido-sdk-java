// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.stage;

import io.kaleido.wfe.sdk.protocol.HandlerEvent;
import io.kaleido.wfe.sdk.protocol.PatchOp;
import io.kaleido.wfe.sdk.protocol.Trigger;

import java.time.Instant;
import java.util.List;

public sealed interface EvalResult {
    EvalResultType type();
    String message();
    Instant deadline();
    List<Trigger> triggers();
    List<PatchOp> extraUpdates();
    List<HandlerEvent> events();
    String errorCode();
    com.fasterxml.jackson.databind.JsonNode errorData();

    static EvalResult complete() {
        return new Impl(EvalResultType.COMPLETE, null, null, null, null, null, null, null);
    }

    static EvalResult waiting() {
        return new Impl(EvalResultType.WAITING, null, null, null, null, null, null, null);
    }

    static EvalResult waiting(Instant deadline) {
        return new Impl(EvalResultType.WAITING, null, deadline, null, null, null, null, null);
    }

    static EvalResult fixableError(String message) {
        return new Impl(EvalResultType.FIXABLE_ERROR, message, null, null, null, null, null, null);
    }

    static EvalResult transientError(String message) {
        return new Impl(EvalResultType.TRANSIENT_ERROR, message, null, null, null, null, null, null);
    }

    static EvalResult hardFailure(String message) {
        return new Impl(EvalResultType.HARD_FAILURE, message, null, null, null, null, null, null);
    }

    static EvalResult hardFailure(String message, String errorCode, com.fasterxml.jackson.databind.JsonNode errorData) {
        return new Impl(EvalResultType.HARD_FAILURE, message, null, null, null, null, errorCode, errorData);
    }

    default EvalResult withTriggers(List<Trigger> triggers) {
        return new Impl(type(), message(), deadline(), triggers, extraUpdates(), events(), errorCode(), errorData());
    }

    default EvalResult withExtraUpdates(List<PatchOp> updates) {
        return new Impl(type(), message(), deadline(), triggers(), updates, events(), errorCode(), errorData());
    }

    default EvalResult withEvents(List<HandlerEvent> events) {
        return new Impl(type(), message(), deadline(), triggers(), extraUpdates(), events, errorCode(), errorData());
    }

    default EvalResult withDeadline(Instant deadline) {
        return new Impl(type(), message(), deadline, triggers(), extraUpdates(), events(), errorCode(), errorData());
    }

    default EvalResult withErrorCode(String errorCode) {
        return new Impl(type(), message(), deadline(), triggers(), extraUpdates(), events(), errorCode, errorData());
    }

    default EvalResult withErrorData(com.fasterxml.jackson.databind.JsonNode errorData) {
        return new Impl(type(), message(), deadline(), triggers(), extraUpdates(), events(), errorCode(), errorData);
    }

    record Impl(
            EvalResultType type,
            String message,
            Instant deadline,
            List<Trigger> triggers,
            List<PatchOp> extraUpdates,
            List<HandlerEvent> events,
            String errorCode,
            com.fasterxml.jackson.databind.JsonNode errorData
    ) implements EvalResult {}
}
