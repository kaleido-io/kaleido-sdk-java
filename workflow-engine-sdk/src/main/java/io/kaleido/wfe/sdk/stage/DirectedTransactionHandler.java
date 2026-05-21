// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.stage;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.wfe.sdk.handlers.TransactionHandler;
import io.kaleido.wfe.sdk.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public abstract class DirectedTransactionHandler<T extends WithStageDirector> implements TransactionHandler {

    private static final Logger log = LoggerFactory.getLogger(DirectedTransactionHandler.class);

    private final String handlerName;
    private final Map<String, DirectedActionConfig<T>> actionMap;
    private final Class<T> inputType;

    protected DirectedTransactionHandler(String name, Class<T> inputType,
                                         Map<String, DirectedActionConfig<T>> actionMap) {
        this.handlerName = name;
        this.inputType = inputType;
        this.actionMap = actionMap;
    }

    @Override
    public String name() {
        return handlerName;
    }

    @Override
    public WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) throws Exception {
        var results = evalDirected(request.transactions());
        return WSHandleTransactionsResult.forRequest(request, results);
    }

    protected List<WSHandleTransactionResult> evalDirected(List<WSHandleTransaction> transactions) throws Exception {
        var results = new WSHandleTransactionResult[transactions.size()];

        var byAction = new LinkedHashMap<String, List<int[]>>();
        var parsedInputs = new ArrayList<T>();
        var rawInputs = new ArrayList<JsonNode>();

        for (int i = 0; i < transactions.size(); i++) {
            var txn = transactions.get(i);
            T parsed = JSON.MAPPER.treeToValue(txn.input(), inputType);
            parsedInputs.add(parsed);
            rawInputs.add(txn.input());
            var action = parsed.getStageDirector().action();
            byAction.computeIfAbsent(action, k -> new ArrayList<>())
                    .add(new int[]{i});
        }

        for (var entry : byAction.entrySet()) {
            var actionName = entry.getKey();
            var indices = entry.getValue();
            var config = actionMap.get(actionName);

            if (config == null) {
                for (var idx : indices) {
                    results[idx[0]] = WSHandleTransactionResult.error(
                            "Unknown action: " + actionName);
                }
                continue;
            }

            if (config.invocationMode() == InvocationMode.PARALLEL) {
                @SuppressWarnings("unchecked")
                var futures = new CompletableFuture[indices.size()];
                for (int j = 0; j < indices.size(); j++) {
                    int index = indices.get(j)[0];
                    T parsed = parsedInputs.get(index);
                    JsonNode rawInput = rawInputs.get(index);
                    futures[j] = CompletableFuture.supplyAsync(() -> {
                        try {
                            var evalResult = config.handler().apply(parsed);
                            return StageDirectorHelper.mapOutput(
                                    parsed.getStageDirector(), evalResult, rawInput);
                        } catch (Exception e) {
                            return classifyAndMap(parsed.getStageDirector(), e, rawInput);
                        }
                    });
                }
                CompletableFuture.allOf(futures).join();
                for (int j = 0; j < indices.size(); j++) {
                    results[indices.get(j)[0]] = (WSHandleTransactionResult) futures[j].join();
                }
            } else {
                var parsedList = new ArrayList<T>();
                for (var idx : indices) {
                    parsedList.add(parsedInputs.get(idx[0]));
                }
                var evalResults = config.batchHandler().apply(parsedList);
                for (int j = 0; j < indices.size(); j++) {
                    int index = indices.get(j)[0];
                    results[index] = StageDirectorHelper.mapOutput(
                            parsedInputs.get(index).getStageDirector(),
                            evalResults.get(j),
                            rawInputs.get(index));
                }
            }
        }

        return Arrays.asList(results);
    }

    private WSHandleTransactionResult classifyAndMap(StageDirector director, Exception e, JsonNode rawInput) {
        var errorMap = director.errorMap();
        if (errorMap != null && !errorMap.isEmpty()) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            for (var mapping : errorMap) {
                if (mapping.pattern() == null) continue;
                try {
                    if (Pattern.compile(mapping.pattern()).matcher(msg).find()) {
                        var classified = switch (mapping.type()) {
                            case HARD_FAILURE -> EvalResult.hardFailure(msg);
                            case TRANSIENT_ERROR -> EvalResult.transientError(msg);
                            case FIXABLE_ERROR -> EvalResult.fixableError(msg);
                            default -> EvalResult.transientError(msg);
                        };
                        return StageDirectorHelper.mapOutput(director, classified, rawInput);
                    }
                } catch (Exception regexEx) {
                    log.warn("Invalid errorMap regex pattern '{}': {}", mapping.pattern(), regexEx.getMessage());
                }
            }
        }
        return WSHandleTransactionResult.error(e.getMessage());
    }
}
