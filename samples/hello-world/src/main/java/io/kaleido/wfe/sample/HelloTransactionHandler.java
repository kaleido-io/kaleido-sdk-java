// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sample;

import io.kaleido.wfe.sdk.handlers.TransactionHandler;
import io.kaleido.wfe.sdk.protocol.WSHandleTransactions;
import io.kaleido.wfe.sdk.protocol.WSHandleTransactionResult;
import io.kaleido.wfe.sdk.protocol.WSHandleTransactionsResult;
import io.kaleido.wfe.sdk.spring.KaleidoTransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@KaleidoTransactionHandler("hello")
public class HelloTransactionHandler implements TransactionHandler {

    private static final Logger log = LoggerFactory.getLogger(HelloTransactionHandler.class);

    @Override
    public String name() {
        return "hello";
    }

    @Override
    public WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) {
        log.info("Handling batch of {} transactions", request.transactions().size());
        var results = request.transactions().stream()
                .map(txn -> {
                    log.info("Transaction {} stage={}", txn.transactionId(), txn.stage());
                    return WSHandleTransactionResult.stage("complete");
                })
                .toList();
        return WSHandleTransactionsResult.forRequest(request, results);
    }
}
