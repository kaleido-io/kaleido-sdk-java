// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sample;

import io.kaleido.wfe.sdk.handlers.TransactionHandler;
import io.kaleido.wfe.sdk.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class HelloTransactionHandler implements TransactionHandler {

    private static final Logger log = LoggerFactory.getLogger(HelloTransactionHandler.class);

    @Override
    public String name() {
        return "hello";
    }

    @Override
    public WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) {
        log.info("Handling batch of {} transactions", request.transactions().size());

        var results = new ArrayList<WSHandleTransactionResult>();
        for (var txn : request.transactions()) {
            log.info("Transaction {} stage={} idempotencyKey={}",
                    txn.transactionId(), txn.stage(), txn.idempotencyKey());
            results.add(WSHandleTransactionResult.stage("complete"));
        }

        return WSHandleTransactionsResult.forRequest(request, results);
    }
}
