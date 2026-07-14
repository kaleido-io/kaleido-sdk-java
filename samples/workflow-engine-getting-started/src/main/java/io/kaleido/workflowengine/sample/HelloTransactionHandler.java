// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample;

import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.handlers.TransactionHandler;
import io.kaleido.workflowengine.sdk.protocol.WSEvaluateReplyResult;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloTransactionHandler implements TransactionHandler {

    private static final Logger log = LoggerFactory.getLogger(HelloTransactionHandler.class);

    @Override
    public String name() {
        return "hello";
    }

    @Override
    public void transactionHandlerBatch(RequestContext reqContext,
                                        WSHandleTransactionsResult result,
                                        WSHandleTransactions batch) {
        log.info("Handling batch of {} transactions", batch.transactions().size());

        for (var txn : batch.transactions()) {
            log.info("Transaction {} stage={} idempotencyKey={}",
                    txn.transactionId(), txn.stage(), txn.idempotencyKey());
            result.getResults().add(WSEvaluateReplyResult.stage("complete"));
        }
    }
}
