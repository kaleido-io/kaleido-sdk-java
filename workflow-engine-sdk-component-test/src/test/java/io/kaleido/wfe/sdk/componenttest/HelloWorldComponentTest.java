// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.componenttest;

import io.kaleido.wfe.sdk.client.WFEWebSocketClient;
import io.kaleido.wfe.sdk.componenttest.harness.TestWorkflowEngine;
import io.kaleido.wfe.sdk.config.RuntimeConfig;
import io.kaleido.wfe.sdk.handlers.HandlerSetFor;
import io.kaleido.wfe.sdk.protocol.JSON;
import io.kaleido.wfe.sdk.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

import java.net.URI;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Component tests against the hello-world workflow.
 * Exercises: single-stage success, hard failure, transient error with retry.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelloWorldComponentTest {

    private TestWorkflowEngine wfe;
    private WFEWebSocketClient client;
    private String workflowId;
    private final AtomicInteger greetCallCount = new AtomicInteger(0);
    private volatile boolean shouldFail = false;
    private volatile boolean shouldTransientFail = false;

    record GreetInput(StageDirector stageDirector, String name) implements WithStageDirector {
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

        var handler = new DirectedTransactionHandler<GreetInput>(
                "greeter", GreetInput.class, Map.of(
                "greet", DirectedActionConfig.parallel(input -> {
                    greetCallCount.incrementAndGet();
                    if (shouldFail) {
                        return EvalResult.hardFailure("unknown person");
                    }
                    if (shouldTransientFail && greetCallCount.get() <= 1) {
                        return EvalResult.transientError("temporary glitch");
                    }
                    var output = JSON.MAPPER.createObjectNode()
                            .put("message", "Hello, " + input.name() + "!");
                    return EvalResult.complete();
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
        greetCallCount.set(0);
        shouldFail = false;
        shouldTransientFail = false;
    }

    @Test
    void singleStageSuccess() throws Exception {
        var input = JSON.MAPPER.createObjectNode().put("name", "World");
        var txn = wfe.submitTransaction(workflowId, "greet", input);
        String txnId = txn.path("id").asText();

        var result = wfe.waitForTransaction(txnId);

        assertEquals("success", result.path("status").asText());
        assertEquals("done", result.path("stage").asText());
    }

    @Test
    void hardFailureTransitionsToFailureStage() throws Exception {
        shouldFail = true;

        var input = JSON.MAPPER.createObjectNode().put("name", "Nobody");
        var txn = wfe.submitTransaction(workflowId, "greet", input);
        String txnId = txn.path("id").asText();

        var result = wfe.waitForTransaction(txnId);

        assertEquals("failure", result.path("status").asText());
        assertEquals("failed", result.path("stage").asText());
    }

    @Test
    void transientErrorRetries() throws Exception {
        shouldTransientFail = true;

        var input = JSON.MAPPER.createObjectNode().put("name", "Retry");
        var txn = wfe.submitTransaction(workflowId, "greet", input);
        String txnId = txn.path("id").asText();

        var result = wfe.waitForTransaction(txnId);

        assertEquals("success", result.path("status").asText());
        assertEquals("done", result.path("stage").asText());
        assertTrue(greetCallCount.get() >= 2, "Handler should be called at least twice");
    }
}
