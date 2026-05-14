// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sample.erc20;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kaleido.wfe.sdk.stage.StageDirector;
import io.kaleido.wfe.sdk.stage.WithStageDirector;

/**
 * Input payload for the ERC-20 transfer handler. Sent by the workflow engine
 * as the {@code input} field of a {@code handle_transactions} message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ERC20TransferInput(
        @JsonProperty("stageDirector") StageDirector stageDirector,
        String contractAddress,
        String to,
        String amount
) implements WithStageDirector {

    @Override
    public StageDirector getStageDirector() {
        return stageDirector;
    }
}
