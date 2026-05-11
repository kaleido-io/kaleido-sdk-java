// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.kaleido.wfe.sdk.protocol.PatchOp;
import io.kaleido.wfe.sdk.protocol.WSHandleTransactionResult;

import java.util.ArrayList;
import java.util.List;

public final class StageDirectorHelper {
    private StageDirectorHelper() {}

    public static WSHandleTransactionResult mapOutput(StageDirector director, EvalResult result, JsonNode output) {
        var stateUpdates = new ArrayList<PatchOp>();
        if (output != null && director.outputPath() != null && !director.outputPath().isEmpty()) {
            stateUpdates.add(PatchOp.replace(director.outputPath(), output));
        }
        if (result.extraUpdates() != null) {
            stateUpdates.addAll(result.extraUpdates());
        }

        String stage = null;
        String error = null;
        String deadline = null;

        switch (result.type()) {
            case COMPLETE -> stage = director.nextStage();
            case WAITING -> {
                if (result.deadline() != null) {
                    deadline = result.deadline().toString();
                }
            }
            case FIXABLE_ERROR -> error = result.message();
            case TRANSIENT_ERROR -> error = result.message();
            case HARD_FAILURE -> {
                stage = director.failureStage() != null ? director.failureStage() : director.nextStage();
                error = result.message();
            }
        }

        return new WSHandleTransactionResult(
                stage,
                error,
                null,
                stateUpdates.isEmpty() ? null : stateUpdates,
                result.triggers(),
                result.events(),
                deadline);
    }
}
