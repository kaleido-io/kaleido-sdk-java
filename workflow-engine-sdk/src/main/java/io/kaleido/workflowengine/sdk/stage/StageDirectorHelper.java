// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kaleido.workflowengine.sdk.errors.SDKErrors;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.PatchOp;
import io.kaleido.workflowengine.sdk.protocol.WSEvaluateTransaction;
import io.kaleido.workflowengine.sdk.protocol.WSEvaluateReplyResult;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stage Director helper.
 *
 * <p>Provides the StageDirector pattern for building composable transaction
 * handlers, and the batch evaluation loop that routes transactions to their
 * configured actions.
 */
public final class StageDirectorHelper {
    private StageDirectorHelper() {}

    private static final Logger log = LoggerFactory.getLogger(StageDirectorHelper.class);

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Maps an action outcome to a WebSocket reply result.
     */
    public static WSEvaluateReplyResult mapOutput(
            StageDirector stageDirector,
            WSEvaluateTransaction transaction,
            ActionResult actionResult) {

        var replyResult = new WSEvaluateReplyResult();
        var result = actionResult.result();
        var error = actionResult.error();
        var output = actionResult.output();
        var customStage = actionResult.customStage();
        var deadline = actionResult.deadline();

        if (actionResult.triggers() != null && !actionResult.triggers().isEmpty()) {
            replyResult.setTriggers(actionResult.triggers());
        }

        if (actionResult.events() != null && !actionResult.events().isEmpty()) {
            replyResult.setEvents(actionResult.events());
        }

        // Serialize output to state updates
        if (output != null && !output.isNull()) {
            if (stageDirector.outputPath() == null || stageDirector.outputPath().isEmpty()) {
                log.error("Transaction {} cannot store output as outputPath is missing", transaction.transactionId());
                if (error == null) {
                    error = SDKErrors.newError(SDKErrors.MSG_DIRECTOR_OUTPUT_PATH_MISSING);
                    result = EvalResult.FIXABLE_ERROR;
                }
            } else {
                var stateUpdates = new ArrayList<PatchOp>();
                stateUpdates.add(PatchOp.add(stageDirector.outputPath(), output));
                replyResult.setStateUpdates(stateUpdates);
            }
        }

        // Append any extra state updates provided by the handler
        if (actionResult.extraUpdates() != null && !actionResult.extraUpdates().isEmpty()) {
            var stateUpdates = replyResult.getStateUpdates() != null
                    ? new ArrayList<>(replyResult.getStateUpdates())
                    : new ArrayList<PatchOp>();
            stateUpdates.addAll(actionResult.extraUpdates());
            replyResult.setStateUpdates(stateUpdates);
        }

        if (deadline != null && result != EvalResult.WAITING) {
            if (error == null) {
                error = SDKErrors.newError(SDKErrors.MSG_DEADLINE_NOT_WAITING, stageDirector.action(), result);
            }
            result = EvalResult.FIXABLE_ERROR;
        }

        switch (result) {
            case HARD_FAILURE -> {
                var failureStage = stageDirector.failureStage();
                if ((failureStage == null || failureStage.isEmpty()) && customStage == null) {
                    log.error("Transaction {} cannot be transitioned due to missing failureStage", transaction.transactionId());
                    if (error == null) {
                        error = SDKErrors.newError(SDKErrors.MSG_DIRECTOR_FAILURE_STAGE_MISSING);
                    }
                    replyResult.setError(error.getMessage());
                    log.debug("Transaction {} encountered error: {}", transaction.transactionId(), error.getMessage());
                } else {
                    var next = customStage != null ? customStage : failureStage;
                    replyResult.setStage(next);

                    // Store error in state at /error path (and error data at /errorData)
                    if (error != null) {
                        var stateUpdates = replyResult.getStateUpdates() != null
                                ? new ArrayList<>(replyResult.getStateUpdates())
                                : new ArrayList<PatchOp>();
                        stateUpdates.add(PatchOp.add("/error", (Object) error.getMessage()));
                        if (actionResult.errorData() != null) {
                            stateUpdates.add(PatchOp.add("/errorData", actionResult.errorData()));
                        }
                        replyResult.setStateUpdates(stateUpdates);
                    }
                    log.debug("Transaction {} directed to failureStage '{}'", transaction.transactionId(), next);
                }
            }
            case COMPLETE -> {
                var subflow = actionResult.subflow() != null ? actionResult.subflow() : stageDirector.nextSubflow();
                if (subflow != null && !subflow.isEmpty() && customStage == null && stageDirector.nextStage() == null) {
                    replyResult.setSubflow(subflow);
                    log.debug("Transaction {} evaluated successfully and will transition to subflow '{}'",
                            transaction.transactionId(), subflow);
                } else {
                    var next = customStage != null ? customStage : stageDirector.nextStage();
                    if (next == null || next.isEmpty()) {
                        error = SDKErrors.newError(SDKErrors.MSG_DIRECTOR_NEXT_STAGE_MISSING);
                        return WSEvaluateReplyResult.error(error.getMessage());
                    }
                    replyResult.setStage(next);
                    log.debug("Transaction {} evaluated successfully and will transition to nextStage '{}'",
                            transaction.transactionId(), next);
                }
            }
            case WAITING -> {
                if (deadline != null) {
                    replyResult.setDeadline(deadline);
                }
                log.debug("Transaction {} evaluated successfully and will remain in stage", transaction.transactionId());
            }
            default -> {
                if (error != null) {
                    replyResult.setError(error.getMessage());
                    log.debug("Transaction {} encountered error: {}", transaction.transactionId(), error.getMessage());
                }
            }
        }

        return replyResult;
    }

    /**
     * Evaluate a batch of directed transactions: groups transactions by action,
     * executes them according to their invocation mode (PARALLEL or BATCH) and
     * maps the results back onto the supplied {@code reply}.
     *
     * <p>Inputs are deserialized to {@code inputType} with Jackson. When the
     * input JSON has no {@code stageDirector} property, one is synthesized from
     * the flat {@code action}/{@code outputPath}/{@code nextStage}/
     * {@code nextSubflow}/{@code failureStage} fields before deserialization.
     */
    public static <T extends WithStageDirector> void evalDirected(
            WSHandleTransactionsResult reply,
            WSHandleTransactions batch,
            Map<String, ActionConfig<T>> actionMap,
            Class<T> inputType) throws Exception {

        var transactions = batch.transactions();
        var results = new WSEvaluateReplyResult[transactions.size()];

        record ExecutableTransaction<T>(int idx, WSEvaluateTransaction transaction, T input) {}

        var byAction = new LinkedHashMap<String, List<ExecutableTransaction<T>>>();

        // Phase 1: Parse inputs and group by action
        for (var i = 0; i < transactions.size(); i++) {
            var req = transactions.get(i);
            log.debug("Transaction id={},workflow={},stage={} evaluating",
                    req.transactionId(), req.workflowId(), req.stage());

            T input;
            try {
                input = parseInput(req, inputType);
            } catch (Exception e) {
                log.error("Transaction id={} could not be parsed: {}", req.transactionId(), e.getMessage());
                results[i] = WSEvaluateReplyResult.error("Input parsing error: " + e.getMessage());
                continue;
            }

            var sd = input.stageDirector();
            var actionConf = actionMap.get(sd.action());

            if (actionConf == null) {
                results[i] = WSEvaluateReplyResult.error(
                        "Invalid action '" + sd.action() + "' for handler '" + batch.handler() + "'");
                continue;
            }

            byAction.computeIfAbsent(sd.action(), k -> new ArrayList<>())
                    .add(new ExecutableTransaction<>(i, req, input));
        }

        // Phase 2: Execute transactions by action
        var completions = new ArrayList<CompletableFuture<Void>>();

        for (var entry : byAction.entrySet()) {
            var actionConf = actionMap.get(entry.getKey());
            var actionTransactions = entry.getValue();

            switch (actionConf.invocationMode()) {
                case PARALLEL -> {
                    for (var req : actionTransactions) {
                        completions.add(CompletableFuture.runAsync(() ->
                                results[req.idx()] = execMapped(actionConf, req.transaction(), req.input()),
                                EXECUTOR));
                    }
                }
                case BATCH -> completions.add(CompletableFuture.runAsync(() -> {
                    var batchIn = actionTransactions.stream()
                            .map(r -> new TransactionHandlerBatchIn<>(r.transaction(), r.input()))
                            .toList();
                    var batchOut = execBatchMapped(actionConf, batchIn);
                    for (var j = 0; j < actionTransactions.size(); j++) {
                        results[actionTransactions.get(j).idx()] = batchOut.get(j);
                    }
                }, EXECUTOR));
            }
        }

        // Phase 3: Wait for all completions
        CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new)).join();

        reply.setResults(new ArrayList<>(Arrays.asList(results)));
    }

    private static <T extends WithStageDirector> T parseInput(
            WSEvaluateTransaction req, Class<T> inputType) throws Exception {

        var inputNode = req.input();
        if (inputNode == null || inputNode.isNull()) {
            throw SDKErrors.newError(SDKErrors.MSG_INPUT_NULL_OR_UNDEFINED, req.stage());
        }

        // If input is a plain object from JSON (no stageDirector property),
        // synthesize one from the action/outputPath/nextStage/failureStage fields
        if (inputNode.isObject() && !inputNode.has("stageDirector")) {
            if (!inputNode.has("action") || inputNode.get("action").isNull()) {
                var fieldNames = new ArrayList<String>();
                inputNode.fieldNames().forEachRemaining(fieldNames::add);
                throw SDKErrors.newError(SDKErrors.MSG_MISSING_ACTION_FIELD,
                        req.stage(), String.join(", ", fieldNames));
            }
            var withDirector = (ObjectNode) inputNode.deepCopy();
            var director = withDirector.putObject("stageDirector");
            copyField(inputNode, director, "action");
            copyField(inputNode, director, "outputPath");
            copyField(inputNode, director, "nextStage");
            copyField(inputNode, director, "nextSubflow");
            copyField(inputNode, director, "failureStage");
            inputNode = withDirector;
        }

        var input = JSON.MAPPER.treeToValue(inputNode, inputType);
        if (input == null || input.stageDirector() == null) {
            throw SDKErrors.newError(SDKErrors.MSG_INPUT_NULL_OR_UNDEFINED, req.stage());
        }
        return input;
    }

    private static void copyField(JsonNode from, ObjectNode to, String field) {
        if (from.has(field)) {
            to.set(field, from.get(field));
        }
    }

    private static <T extends WithStageDirector> WSEvaluateReplyResult execMapped(
            ActionConfig<T> config, WSEvaluateTransaction transaction, T input) {
        try {
            if (config.handler() == null) {
                throw SDKErrors.newError(SDKErrors.MSG_HANDLER_NOT_CONFIGURED);
            }
            var handlerResult = config.handler().handle(transaction, input);
            return mapOutput(input.stageDirector(), transaction, handlerResult);
        } catch (Exception e) {
            log.error("Handler execution failed", e);
            return WSEvaluateReplyResult.error(e.getMessage());
        }
    }

    private static <T extends WithStageDirector> List<WSEvaluateReplyResult> execBatchMapped(
            ActionConfig<T> config, List<TransactionHandlerBatchIn<T>> transactions) {
        try {
            if (config.batchHandler() == null) {
                throw SDKErrors.newError(SDKErrors.MSG_BATCH_HANDLER_NOT_CONFIGURED);
            }
            var batchResults = config.batchHandler().handle(transactions);
            if (batchResults.size() != transactions.size()) {
                throw SDKErrors.newError(SDKErrors.MSG_BATCH_HANDLER_RESULT_COUNT_MISMATCH,
                        batchResults.size(), transactions.size());
            }
            var mapped = new ArrayList<WSEvaluateReplyResult>(transactions.size());
            for (var i = 0; i < transactions.size(); i++) {
                var req = transactions.get(i);
                mapped.add(mapOutput(req.value().stageDirector(), req.transaction(), batchResults.get(i)));
            }
            return mapped;
        } catch (Exception e) {
            log.error("Batch handler execution failed", e);
            return transactions.stream()
                    .map(t -> WSEvaluateReplyResult.error(e.getMessage()))
                    .toList();
        }
    }
}
