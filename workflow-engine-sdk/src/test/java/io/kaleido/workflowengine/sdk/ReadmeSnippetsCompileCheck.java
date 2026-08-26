// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk;

import io.kaleido.workflowengine.sdk.app.EventProcessorDef;
import io.kaleido.workflowengine.sdk.client.WorkflowEngineClient;
import io.kaleido.workflowengine.sdk.config.AuthConfig;
import io.kaleido.workflowengine.sdk.config.ClientConfig;
import io.kaleido.workflowengine.sdk.factories.EventSourceEvent;
import io.kaleido.workflowengine.sdk.factories.EventSourceFactory;
import io.kaleido.workflowengine.sdk.factories.EventSourcePollOutput;
import io.kaleido.workflowengine.sdk.factories.TransactionHandlerFactory;
import io.kaleido.workflowengine.sdk.handlers.EngineAPI;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.handlers.TransactionHandler;
import io.kaleido.workflowengine.sdk.protocol.AsyncTransactionInput;
import io.kaleido.workflowengine.sdk.protocol.IdempotentSubmitResult;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.PatchOp;
import io.kaleido.workflowengine.sdk.protocol.Trigger;
import io.kaleido.workflowengine.sdk.protocol.WSEvaluateReplyResult;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;
import io.kaleido.workflowengine.sdk.stage.ActionConfig;
import io.kaleido.workflowengine.sdk.stage.ActionResult;
import io.kaleido.workflowengine.sdk.stage.StageDirector;
import io.kaleido.workflowengine.sdk.stage.WithStageDirector;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Compile-only mirror of the code snippets in the README, so API drift breaks
 * the build rather than silently rotting the docs. Never executed.
 */
@SuppressWarnings("unused")
final class ReadmeSnippetsCompileCheck {
    private ReadmeSnippetsCompileCheck() {}

    public record ProcessInput(StageDirector stageDirector, String userId, double amount)
            implements WithStageDirector {}

    public record MyConfig(int batchSize, List<String> allowlist) {}

    static void clientConstruction() throws Exception {
        var fromEnv = WorkflowEngineClient.fromConfigFile();
        var fromPath = WorkflowEngineClient.fromConfigFile(Path.of("/path/to/config.yaml"));
        var programmatic = new WorkflowEngineClient(ClientConfig.builder()
                .wsUrl(URI.create("ws://localhost:5503/ws"))
                .providerName("my-service")
                .auth(new AuthConfig.TokenAuth("your-token", "X-Kld-Authz", null))
                .build());
    }

    static void transactionHandlerExample() throws Exception {
        Map<String, ActionConfig<ProcessInput>> actionMap = Map.of(
            "validatePayment", ActionConfig.parallel((transaction, input) -> {
                if (input.amount() <= 0) {
                    return ActionResult.hardFailure(new IllegalArgumentException("Invalid amount"));
                }
                return ActionResult.complete()
                        .withOutput(Map.of("validated", true))
                        .withExtraUpdates(List.of(PatchOp.add("/validation", Map.of("valid", true))));
            }),
            "processPayment", ActionConfig.parallel((transaction, input) -> {
                var receipt = Map.of("receipt", input.userId());
                return ActionResult.complete()
                        .withOutput(receipt)
                        .withTriggers(List.of(new Trigger("payment.completed", null)));
            }));

        var handler = TransactionHandlerFactory.createTransactionHandler(
                "payment-handler", ProcessInput.class, actionMap);

        WorkflowEngineClient.fromConfigFile()
                .transactionHandler("payment-handler", handler)
                .start();
    }

    static class SpawningHandler implements TransactionHandler {
        private EngineAPI engineAPI;

        @Override
        public String name() { return "spawning-handler"; }

        @Override
        public void init(EngineAPI engineAPI) { this.engineAPI = engineAPI; }

        @Override
        public void transactionHandlerBatch(RequestContext reqContext,
                                            WSHandleTransactionsResult result,
                                            WSHandleTransactions batch) throws Exception {
            for (var transaction : batch.transactions()) {
                List<IdempotentSubmitResult> submissions = engineAPI.submitAsyncTransactions(
                        reqContext, transaction.authRef(),
                        List.of(new AsyncTransactionInput(null, null,
                                JSON.MAPPER.valueToTree("flw:abc123"), "process",
                                JSON.MAPPER.valueToTree(Map.of("data", "value")), null)));
                result.getResults().add(WSEvaluateReplyResult.stage("submitted"));
            }
        }
    }

    static void eventSourceExample() throws Exception {
        var mySource = EventSourceFactory.createEventSource("my-event-source",
                (conf, checkpointIn, authRef) -> {
                    long lastId = checkpointIn != null ? checkpointIn.path("lastId").asLong() : 0;
                    return EventSourcePollOutput.of(
                            Map.of("lastId", lastId + 1),
                            List.of(EventSourceEvent.of("evt-" + (lastId + 1), "my-topic",
                                    Map.of("value", lastId + 1))));
                });

        WorkflowEngineClient.fromConfigFile()
                .eventSource(mySource)
                .start();
    }

    static void eventProcessorExample() throws Exception {
        WorkflowEngineClient.fromConfigFile()
                .eventProcessor("my-processor", EventProcessorDef.of(
                        (ctx, events) -> {
                            var config = ctx.config(MyConfig.class);
                            for (var event : events) {
                                // persist(event, config.batchSize());
                            }
                        },
                        ctx -> {
                            // optional setup hook: ensure streams, bootstrap resources...
                        }))
                .start();
    }
}
