// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample.componenttest;

import io.kaleido.workflowengine.sdk.client.WorkflowEngineClient;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sample.HelloTransactionHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the sample's real {@link HelloTransactionHandler} completes a
 * transaction dispatched by a live workflow engine: post the workflow in
 * {@code workflows/hello.yaml}, submit a transaction against it, and confirm
 * it reaches the {@code complete} stage that {@code hello} replies with.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelloTransactionTest {

    private static final Logger log = LoggerFactory.getLogger(HelloTransactionTest.class);

    private final String flowEngineUrl = TestConfig.flowEngineUrl();
    private String workflowId;
    private WorkflowEngineClient client;

    @BeforeAll
    void setUp() throws Exception {
        client = new WorkflowEngineClient(TestConfig.clientConfig("hello-sample"));
        client.registerHandler(new HelloTransactionHandler());
        client.connect();
        log.info("Client connected as provider 'hello-sample'");
    }

    @AfterAll
    void tearDown() {
        if (client != null) {
            client.disconnect();
        }
        if (workflowId != null) {
            FetchUtils.delete(flowEngineUrl + "/api/v1/workflows/" + workflowId);
        }
    }

    @Test
    @Timeout(60)
    void helloCompletesADispatchedTransaction() throws Exception {
        var workflowYaml = new String(
                getClass().getClassLoader().getResourceAsStream("workflows/hello.yaml").readAllBytes(),
                StandardCharsets.UTF_8);
        var workflowYamlWithMeta = "name: hello-sample-" + System.currentTimeMillis()
                + "\nversion: \"1.0\"\n" + workflowYaml;

        var flowResponse = FetchUtils.postYaml(flowEngineUrl + "/api/v1/workflows", workflowYamlWithMeta);
        assertTrue(flowResponse.statusCode() < 300,
                "Failed to create workflow: " + flowResponse.statusCode() + " " + flowResponse.body());
        var workflow = JSON.MAPPER.readTree(flowResponse.body());
        workflowId = workflow.get("id").asText();
        log.info("Workflow created: {}", workflowId);

        var body = JSON.MAPPER.createObjectNode();
        body.put("workflowId", workflowId);
        body.put("operation", "greet");
        body.putObject("input");

        var txResponse = FetchUtils.postJson(flowEngineUrl + "/api/v1/transactions",
                JSON.MAPPER.writeValueAsString(body));
        assertEquals(200, txResponse.statusCode(), txResponse.body());
        var transaction = JSON.MAPPER.readTree(txResponse.body());
        var transactionId = transaction.get("id").asText();
        log.info("Transaction submitted: {}", transactionId);

        var deadline = System.currentTimeMillis() + 30_000;
        String stage = null;
        while (System.currentTimeMillis() < deadline) {
            var txStateResponse = FetchUtils.get(flowEngineUrl + "/api/v1/transactions/" + transactionId);
            assertEquals(200, txStateResponse.statusCode());
            stage = JSON.MAPPER.readTree(txStateResponse.body()).path("stage").asText();
            if ("complete".equals(stage)) {
                break;
            }
            Thread.sleep(200);
        }

        assertEquals("complete", stage, "Transaction did not reach the 'complete' stage in time");
        log.info("Transaction {} reached stage 'complete'", transactionId);
    }
}
