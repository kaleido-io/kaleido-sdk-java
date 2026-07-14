// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import io.kaleido.workflowengine.sdk.handlers.EngineAPI;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;
import io.kaleido.workflowengine.sdk.stage.ActionConfig;
import io.kaleido.workflowengine.sdk.stage.StageDirectorHelper;
import io.kaleido.workflowengine.sdk.stage.WithStageDirector;

import java.util.Map;

/**
 * Factory for directed transaction handlers: handlers that route each
 * transaction to an action from the input's {@code action} field via the
 * StageDirector pattern.
 */
public final class TransactionHandlerFactory {
    private TransactionHandlerFactory() {}

    /**
     * Create a directed transaction handler.
     *
     * @param name      handler name to register with the workflow engine
     * @param inputType type the transaction input JSON is deserialized to
     * @param actionMap map of action names to their configurations
     */
    public static <T extends WithStageDirector> TransactionHandlerBuilder createTransactionHandler(
            String name, Class<T> inputType, Map<String, ActionConfig<T>> actionMap) {
        return new TransactionHandlerBase<>(name, inputType, actionMap);
    }

    private static final class TransactionHandlerBase<T extends WithStageDirector>
            implements TransactionHandlerBuilder {

        private final String name;
        private final Class<T> inputType;
        private final Map<String, ActionConfig<T>> actionMap;
        private HandlerInitFn initFn;
        private Runnable closeFn;

        private TransactionHandlerBase(String name, Class<T> inputType, Map<String, ActionConfig<T>> actionMap) {
            this.name = name;
            this.inputType = inputType;
            this.actionMap = actionMap;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public TransactionHandlerBuilder withInitFn(HandlerInitFn initFn) {
            this.initFn = initFn;
            return this;
        }

        @Override
        public TransactionHandlerBuilder withCloseFn(Runnable closeFn) {
            this.closeFn = closeFn;
            return this;
        }

        @Override
        public void init(EngineAPI engineAPI) throws Exception {
            if (initFn != null) {
                initFn.init(engineAPI);
            }
        }

        @Override
        public void close() {
            if (closeFn != null) {
                closeFn.run();
            }
        }

        @Override
        public void transactionHandlerBatch(RequestContext reqContext,
                                            WSHandleTransactionsResult result,
                                            WSHandleTransactions batch) throws Exception {
            StageDirectorHelper.evalDirected(result, batch, actionMap, inputType);
        }
    }
}
