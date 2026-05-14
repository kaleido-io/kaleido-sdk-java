// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.spring;

import io.kaleido.wfe.sdk.handlers.EngineAPI;
import io.kaleido.wfe.sdk.handlers.TransactionHandler;
import io.kaleido.wfe.sdk.protocol.WSHandleTransactions;
import io.kaleido.wfe.sdk.protocol.WSHandleTransactionsResult;

/**
 * Wraps a {@link TransactionHandler} bean discovered via {@link KaleidoTransactionHandler}
 * and supplies the annotation-declared name instead of delegating to {@code name()}.
 */
class AnnotatedTransactionHandlerAdapter implements TransactionHandler {

    private final String handlerName;
    private final TransactionHandler delegate;

    AnnotatedTransactionHandlerAdapter(String handlerName, TransactionHandler delegate) {
        this.handlerName = handlerName;
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return handlerName;
    }

    @Override
    public void init(EngineAPI engineAPI) {
        delegate.init(engineAPI);
    }

    @Override
    public WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) throws Exception {
        return delegate.handleTransactionBatch(request);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
