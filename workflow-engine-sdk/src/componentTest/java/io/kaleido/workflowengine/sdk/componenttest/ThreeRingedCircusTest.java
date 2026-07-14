// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.componenttest;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.client.WorkflowEngineClient;
import io.kaleido.workflowengine.sdk.factories.TransactionHandlerFactory;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.PatchOp;
import io.kaleido.workflowengine.sdk.stage.ActionConfig;
import io.kaleido.workflowengine.sdk.stage.ActionResult;
import io.kaleido.workflowengine.sdk.stage.StageDirector;
import io.kaleido.workflowengine.sdk.stage.WithStageDirector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end conformance test against a live workflow engine: visitors circuit
 * through three rings, exercising the StageDirector pattern, JSON Patch state
 * updates, custom stage transitions, config profiles, and TRANSIENT_ERROR
 * retries. Uses the same workflow fixtures as the TS suite so both SDKs are
 * held to the same behavior.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThreeRingedCircusTest {

    private static final Logger log = LoggerFactory.getLogger(ThreeRingedCircusTest.class);

    private static final int CIRCUITS_PER_VISIT = 3;
    private static final List<String> TENTS = List.of("acrobats", "animals", "cowboys");
    private static final List<String> VISITORS = List.of(
            "Terry Jones", "John Cleese", "Eric Idle", "Graham Chapman", "Michael Palin", "Terry Gilliam");
    private static final double FAIL_LIKELIHOOD = 0.03;

    private final String flowEngineUrl = TestConfig.flowEngineUrl();
    private final AtomicInteger failCount = new AtomicInteger();
    private final List<String> createdWorkflows = new ArrayList<>();

    private WorkflowEngineClient client;

    public record CircusInput(StageDirector stageDirector, String visitor, int circuits)
            implements WithStageDirector {}

    @BeforeAll
    void setUp() throws Exception {
        log.info("Starting Three-Ringed Circus component test against {}", flowEngineUrl);

        client = new WorkflowEngineClient(TestConfig.clientConfig("pythons"));

        Map<String, ActionConfig<CircusInput>> actionMap = new HashMap<>();

        actionMap.put("enter", ActionConfig.parallel((transaction, input) -> {
            if (ThreadLocalRandom.current().nextDouble() < FAIL_LIKELIHOOD) {
                return ActionResult.transientError(
                        new RuntimeException(input.visitor() + " gaff " + failCount.incrementAndGet()));
            }
            return ActionResult.complete().withExtraUpdates(List.of(
                    PatchOp.add("/circuits", (Object) 0),
                    PatchOp.add("/visits", (Object) Map.of()),
                    PatchOp.add("/visits/one", (Object) List.of()),
                    PatchOp.add("/visits/two", (Object) List.of()),
                    PatchOp.add("/visits/three", (Object) List.of())));
        }));

        actionMap.put("one", ActionConfig.parallel((transaction, input) -> {
            var result = ActionResult.complete().withExtraUpdates(List.of(
                    PatchOp.add("/visits/one/-", (Object) Instant.now().toString())));
            var configProfile = transaction.configProfile();
            if (configProfile != null && configProfile.path("oneVisitExit").asBoolean(false)) {
                result.withCustomStage("exit");
            }
            return result;
        }));

        actionMap.put("two", ActionConfig.parallel((transaction, input) ->
                ActionResult.complete().withExtraUpdates(List.of(
                        PatchOp.add("/visits/two/-", (Object) Instant.now().toString())))));

        actionMap.put("three", ActionConfig.parallel((transaction, input) -> {
            var newCircuits = input.circuits() + 1;
            var newStage = newCircuits >= CIRCUITS_PER_VISIT ? "exit" : "one";
            return ActionResult.complete()
                    .withCustomStage(newStage)
                    .withExtraUpdates(List.of(
                            PatchOp.replace("/circuits", (Object) newCircuits),
                            PatchOp.add("/visits/three/-", (Object) Instant.now().toString())));
        }));

        var handler = TransactionHandlerFactory.createTransactionHandler("handler1", CircusInput.class, actionMap);
        client.registerTransactionHandler("handler1", handler);

        client.connect();
        log.info("Client connected");
    }

    @AfterAll
    void tearDown() {
        log.info("Cleaning up component test");
        if (client != null) {
            client.disconnect();
        }
        for (var workflowId : createdWorkflows) {
            FetchUtils.delete(flowEngineUrl + "/api/v1/workflows/" + workflowId);
        }
        log.info("Cleanup complete");
    }

    @Test
    @Timeout(180)
    void processesVisitorsThroughAllThreeRings() throws Exception {
        var workflowYaml = new String(
                getClass().getClassLoader().getResourceAsStream("workflows/three-ringed-circus.yaml").readAllBytes(),
                StandardCharsets.UTF_8);
        // Unique name to avoid conflicts (tests use unique names, not DB drop/recreate)
        var workflowYamlWithMeta = "name: three-ringed-circus-" + System.currentTimeMillis()
                + "\nversion: \"1.0\"\n" + workflowYaml;

        var flowResponse = FetchUtils.postYaml(flowEngineUrl + "/api/v1/workflows", workflowYamlWithMeta);
        assertTrue(flowResponse.statusCode() < 300,
                "Failed to create workflow: " + flowResponse.statusCode() + " " + flowResponse.body());
        var workflow = JSON.MAPPER.readTree(flowResponse.body());
        createdWorkflows.add(workflow.get("id").asText());
        log.info("Workflow created: {}", workflow.get("id").asText());

        // Submit a transaction per tent and visitor
        var transactions = new ArrayList<JsonNode>();
        for (var tent : TENTS) {
            for (var visitor : VISITORS) {
                var body = JSON.MAPPER.createObjectNode();
                body.put("workflowId", workflow.get("id").asText());
                body.put("operation", "visit");
                body.putObject("input")
                        .put("ticketNumber", 1000 + transactions.size())
                        .put("visitor", visitor);
                body.putObject("labels").put("tent", tent);

                var txResponse = FetchUtils.postJson(flowEngineUrl + "/api/v1/transactions",
                        JSON.MAPPER.writeValueAsString(body));
                assertEquals(200, txResponse.statusCode(), txResponse.body());
                transactions.add(JSON.MAPPER.readTree(txResponse.body()));
            }
        }
        log.info("Submitted {} transactions", transactions.size());

        // Wait for all transactions to reach the exit stage
        var deadline = System.currentTimeMillis() + 120_000;
        var completed = new HashSet<String>();
        while (completed.size() < transactions.size() && System.currentTimeMillis() < deadline) {
            for (var tx : transactions) {
                var id = tx.get("id").asText();
                if (completed.contains(id)) {
                    continue;
                }
                var txResponse = FetchUtils.get(flowEngineUrl + "/api/v1/transactions/" + id);
                if (txResponse.statusCode() == 200) {
                    var state = JSON.MAPPER.readTree(txResponse.body());
                    if ("exit".equals(state.path("stage").asText())) {
                        completed.add(id);
                        log.info("Transaction {} completed ({}/{})", id, completed.size(), transactions.size());
                    }
                }
            }
            if (completed.size() < transactions.size()) {
                Thread.sleep(500);
            }
        }
        assertEquals(transactions.size(), completed.size(), "Not all transactions completed");

        // Verify each transaction's final state
        Map<String, Set<String>> tentsByVisitor = new HashMap<>();
        for (var i = 0; i < transactions.size(); i++) {
            var id = transactions.get(i).get("id").asText();
            var txResponse = FetchUtils.get(flowEngineUrl + "/api/v1/transactions/" + id);
            assertEquals(200, txResponse.statusCode());
            var txState = JSON.MAPPER.readTree(txResponse.body());

            assertEquals("exit", txState.path("stage").asText());
            assertEquals(1000 + i, txState.at("/state/input/ticketNumber").asInt());
            assertEquals(CIRCUITS_PER_VISIT, txState.at("/state/circuits").asInt());
            assertEquals(CIRCUITS_PER_VISIT, txState.at("/state/visits/one").size());
            assertEquals(CIRCUITS_PER_VISIT, txState.at("/state/visits/two").size());
            assertEquals(CIRCUITS_PER_VISIT, txState.at("/state/visits/three").size());

            var visitor = txState.at("/state/input/visitor").asText();
            var tent = txState.at("/labels/tent").asText();
            var visited = tentsByVisitor.computeIfAbsent(visitor, k -> new HashSet<>());
            assertFalse(visited.contains(tent), "Duplicate tent " + tent + " for " + visitor);
            visited.add(tent);
        }

        // Every visitor visited every tent
        for (var visitor : VISITORS) {
            for (var tent : TENTS) {
                assertTrue(tentsByVisitor.get(visitor).contains(tent),
                        visitor + " missed tent " + tent);
            }
        }
        log.info("All transactions completed successfully (transient failures injected: {})", failCount.get());
    }
}
