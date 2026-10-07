// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.provider;

import io.kaleido.workflowengine.sdk.handlers.Handler;

/**
 * Creates a handler from its {@link HandlerContext}. A runtime that loads handlers by class
 * name calls {@link #create} once per configured handler, so one factory can serve several
 * handlers with different config.
 *
 * <pre>{@code
 * public class PaymentsFactory implements HandlerFactory<TransactionHandler> {
 *     record Config(String currency, int maxAmount) {}
 *
 *     public TransactionHandler create(HandlerContext ctx) {
 *         var config = ctx.config(Config.class);
 *         return TransactionHandlerFactory.createTransactionHandler(ctx.name(), Input.class,
 *                 Map.of("pay", ActionConfig.parallel((tx, in) -> pay(config, ctx, tx, in))));
 *     }
 * }
 * }</pre>
 *
 * <p>Implementations need a public no-arg constructor.
 *
 * @param <H> the handler type: a {@code TransactionHandler}, {@code EventSource} or {@code EventProcessor}
 */
@FunctionalInterface
public interface HandlerFactory<H extends Handler> {

    /**
     * Creates the handler. Throwing stops the runtime from starting, with the reason, so
     * validate config here rather than on the first request.
     *
     * @param context the handler's name, config and service bindings
     * @return the handler
     * @throws Exception when the handler cannot be created
     */
    H create(HandlerContext context) throws Exception;
}
