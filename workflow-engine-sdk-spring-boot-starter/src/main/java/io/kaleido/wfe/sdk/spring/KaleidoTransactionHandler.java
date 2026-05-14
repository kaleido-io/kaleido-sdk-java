// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.spring;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks a bean as a Kaleido transaction handler with the given name.
 *
 * <p>The annotated class must implement
 * {@link io.kaleido.wfe.sdk.handlers.TransactionHandler}. The {@link #value()}
 * is used as the handler name registered with the workflow engine, replacing
 * the need to implement {@code name()} manually.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface KaleidoTransactionHandler {
    /** Handler name registered with the workflow engine. */
    String value();
}
