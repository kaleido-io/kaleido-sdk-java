// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sample.erc20;

import io.kaleido.wfe.sdk.handlers.EngineAPI;
import io.kaleido.wfe.sdk.protocol.AsyncTransactionInput;
import io.kaleido.wfe.sdk.protocol.JSON;
import io.kaleido.wfe.sdk.spring.KaleidoTransactionHandler;
import io.kaleido.wfe.sdk.stage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Two-stage ERC-20 transfer handler using the {@link StageDirector} pattern.
 *
 * <p><b>Stage 1 (submit):</b> extracts transfer parameters from the input,
 * builds an async transaction submission via the engine API, and transitions
 * to stage 2.</p>
 *
 * <p><b>Stage 2 (confirm):</b> receives the async result and marks the
 * transaction complete.</p>
 */
@KaleidoTransactionHandler("erc20-transfer")
public class ERC20TransactionHandler extends DirectedTransactionHandler<ERC20TransferInput> {

    private static final Logger log = LoggerFactory.getLogger(ERC20TransactionHandler.class);

    private EngineAPI engineAPI;

    public ERC20TransactionHandler() {
        super("erc20-transfer", ERC20TransferInput.class, Map.of(
                "submit", DirectedActionConfig.parallel(ERC20TransactionHandler::submitStage),
                "confirm", DirectedActionConfig.parallel(ERC20TransactionHandler::confirmStage)
        ));
    }

    @Override
    public void init(EngineAPI engineAPI) {
        this.engineAPI = engineAPI;
    }

    private static EvalResult submitStage(ERC20TransferInput input) {
        log.info("ERC-20 transfer: {} -> {} amount={} contract={}",
                "sender", input.to(), input.amount(), input.contractAddress());
        return EvalResult.complete();
    }

    private static EvalResult confirmStage(ERC20TransferInput input) {
        log.info("ERC-20 transfer confirmed for contract={}", input.contractAddress());
        return EvalResult.complete();
    }
}
