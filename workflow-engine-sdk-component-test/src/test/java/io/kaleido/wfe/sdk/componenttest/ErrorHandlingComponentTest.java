// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.componenttest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kaleido.wfe.sdk.client.WFEWebSocketClient;
import io.kaleido.wfe.sdk.componenttest.harness.TestWorkflowEngine;
import io.kaleido.wfe.sdk.config.RuntimeConfig;
import io.kaleido.wfe.sdk.handlers.HandlerSetFor;
import io.kaleido.wfe.sdk.protocol.JSON;
import io.kaleido.wfe.sdk.protocol.PatchOp;
import io.kaleido.wfe.sdk.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Component tests for error handling and JSON Patch state updates.
 * Uses the hello-world workflow (single stage) to test error paths and WithExtraUpdates.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ErrorHandlingComponentTest {

    private TestWorkflowEngine wfe;
    private WFEWebSocketClient client;
    private String workflowId;
    private final AtomicInteger callCount = new AtomicInteger(0);
    private volatile String mode = "complete";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestInput(
            @JsonProperty("stageDirector") StageDirector stageDirector,
            String name
    ) implements WithStageDirector {
        @Override
        public StageDirector getStageDirector() {
            return stageDirector;
        }
    }

    @BeforeAll
    void setup() throws Exception {
        Assumptions.assumeTrue(TestWorkflowEngine.isAvailable(),
                "Docker not available -- component tests require Docker");
        wfe = TestWorkflowEngine.start();

        var handler = new DirectedTransactionHandler<TestInput>(
                "greeter", TestInput.class, Map.of(
                "greet", DirectedActionConfig.parallel(input -> {
                    int count = callCount.incrementAndGet();
                    return switch (mode) {
                        case "extra-updates" -> {
                            var updates = List.of(
                                    PatchOp.add("/counters",
                                            JSON.MAPPER.createObjectNode()),
                                    PatchOp.add("/counters/invocations",
                                            JSON.MAPPER.valueToTree(count))
                            );
                            yield EvalResult.complete().withExtraUpdates(updates);
                        }
                        case "transient-then-complete" -> {
                            if (count <= 1) {
                                yield EvalResult.transientError("temporary glitch");
                            }
                            yield EvalResult.complete();
                        }
                        case "hard-failure" -> EvalResult.hardFailure("permanent error");
                        case "fixable-error-then-complete" -> {
                            if (count <= 1) {
                                yield EvalResult.fixableError("fixable issue");
                            }
                            yield EvalResult.complete();
                        }
                        default -> EvalResult.complete();
                    };
                })
        )) {};

        var config = RuntimeConfig.builder()
                .providerName("provider1")
                .url(URI.create(wfe.wsUrl()))
                .build();

        client = new WFEWebSocketClient(config, HandlerSetFor.of(handler));
        client.connect();
        Thread.sleep(1000);

        String yaml = new String(
                getClass().getResourceAsStream("/workflows/hello-world.yaml").readAllBytes(),
                StandardCharsets.UTF_8);
        var workflow = wfe.deployWorkflow("hello-world", yaml);
        workflowId = workflow.path("id").asText();
    }

    @AfterAll
    void teardown() {
        if (client != null) client.close();
        if (wfe != null) wfe.close();
    }

    @BeforeEach
    void resetState() {
        callCount.set(0);
        mode = "complete";
    }

    @Test
    void extraUpdatesWriteState() throws Exception {
        mode = "extra-updates";

        var input = JSON.MAPPER.createObjectNode().put("name", "PatchTest");
        var txn = wfe.submitTransaction(workflowId, "greet", input);
        String txnId = txn.path("id").asText();

        var result = wfe.waitForTransaction(txnId);

        assertEquals("success", result.path("status").asText());
        assertEquals("done", result.path("stage").asText());
        // Verify the extra state updates were applied
        assertNotNull(result.path("state").path("counters"));
    }

    @Test
    void transientErrorRetries() throws Exception {
        mode = "transient-then-complete";

        var input = JSON.MAPPER.createObjectNode().put("name", "RetryMe");
        var txn = wfe.submitTransaction(workflowId, "greet", input);
        String txnId = txn.path("id").asText();

        var result = wfe.waitForTransaction(txnId);

        assertEquals("success", result.path("status").asText());
        assertTrue(callCount.get() >= 2);
    }

    @Test
    void hardFailureTransitions() throws Exception {
        mode = "hard-failure";

        var input = JSON.MAPPER.createObjectNode().put("name", "FailMe");
        var txn = wfe.submitTransaction(workflowId, "greet", input);
        String txnId = txn.path("id").asText();

        var result = wfe.waitForTransaction(txnId);

        assertEquals("failure", result.path("status").asText());
        assertEquals("failed", result.path("stage").asText());
    }

    @Test
    void fixableErrorRetries() throws Exception {
        mode = "fixable-error-then-complete";

        var input = JSON.MAPPER.createObjectNode().put("name", "FixMe");
        var txn = wfe.submitTransaction(workflowId, "greet", input);
        String txnId = txn.path("id").asText();

        var result = wfe.waitForTransaction(txnId);

        assertEquals("success", result.path("status").asText());
        assertTrue(callCount.get() >= 2);
    }
}
