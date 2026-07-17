// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.componenttest;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.client.WorkflowEngineClient;
import io.kaleido.workflowengine.sdk.factories.EventSourceEvent;
import io.kaleido.workflowengine.sdk.factories.EventSourceFactory;
import io.kaleido.workflowengine.sdk.factories.EventSourcePollOutput;
import io.kaleido.workflowengine.sdk.factories.TransactionHandlerFactory;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.Trigger;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end conformance test for event-driven correlation against a live
 * workflow engine: watcher transactions set traps as triggers on card topics,
 * a dealer event source deals the deck, and topic matching fires each trap.
 * Exercises triggers, WAITING results, event sources with checkpointing, and
 * cross-provider correlation. Uses the same workflow fixtures as the TS suite.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SnapTest {

    private static final Logger log = LoggerFactory.getLogger(SnapTest.class);

    private static final List<String> SUITS = List.of("clubs", "diamonds", "hearts", "spades");
    private static final List<String> RANKS = List.of(
            "ace", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "jack", "queen", "king");

    public record PlayingCard(String description, String suit, String rank) {}

    public record SnapHandlerInput(StageDirector stageDirector, String suit, String rank)
            implements WithStageDirector {}

    private final String flowEngineUrl = TestConfig.flowEngineUrl();
    private final List<PlayingCard> deck = newDeck();
    private final Map<String, Boolean> trapsSet = new ConcurrentHashMap<>();
    private final List<String> createdWorkflows = new ArrayList<>();
    private final List<String> createdStreams = new ArrayList<>();

    private WorkflowEngineClient watcherClient;
    private WorkflowEngineClient dealerClient;

    private static List<PlayingCard> newDeck() {
        var deck = new ArrayList<PlayingCard>();
        for (var suit : SUITS) {
            for (var rank : RANKS) {
                deck.add(new PlayingCard(rank + " of " + suit, suit, rank));
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    @BeforeAll
    void setUp() throws Exception {
        log.info("Starting Snap component test against {}", flowEngineUrl);

        watcherClient = new WorkflowEngineClient(TestConfig.clientConfig("provider1"));

        Map<String, ActionConfig<SnapHandlerInput>> watcherActions = new HashMap<>();

        watcherActions.put("set-trap", ActionConfig.parallel((transaction, input) ->
                ActionResult.complete().withTriggers(List.of(
                        new Trigger("suit." + input.suit() + ".rank." + input.rank(), null)))));

        watcherActions.put("trap-set", ActionConfig.parallel((transaction, input) -> {
            var cardTopic = "suit." + input.suit() + ".rank." + input.rank();
            log.info("Trap set: {}", cardTopic);
            trapsSet.put(cardTopic, true);
            return ActionResult.waiting();
        }));

        watcherActions.put("trap-fired", ActionConfig.parallel((transaction, input) -> {
            assertNotNull(transaction.events());
            assertFalse(transaction.events().isEmpty());
            var snap = transaction.events().get(0);
            var cardPlayed = JSON.MAPPER.treeToValue(snap.data(), PlayingCard.class);

            assertEquals(input.suit(), cardPlayed.suit());
            assertEquals(input.rank(), cardPlayed.rank());

            return ActionResult.complete().withOutput(snap.data());
        }));

        var watcherHandler = TransactionHandlerFactory.createTransactionHandler(
                "watcher", SnapHandlerInput.class, watcherActions);
        watcherClient.registerTransactionHandler("watcher", watcherHandler);

        watcherClient.connect();
        log.info("Watcher client connected");
    }

    @AfterAll
    void tearDown() throws Exception {
        log.info("Cleaning up snap test");
        if (watcherClient != null) {
            watcherClient.disconnect();
        }
        if (dealerClient != null) {
            dealerClient.disconnect();
        }
        Thread.sleep(1000);

        for (var streamId : createdStreams) {
            FetchUtils.delete(flowEngineUrl + "/api/v1/streams/" + streamId);
        }
        for (var workflowId : createdWorkflows) {
            FetchUtils.delete(flowEngineUrl + "/api/v1/workflows/" + workflowId);
        }
        log.info("Cleanup complete");
    }

    @Test
    @Timeout(180)
    void playsSnapWithTriggersAndEventMatching() throws Exception {
        var workflowYaml = new String(
                getClass().getClassLoader().getResourceAsStream("workflows/snap.yaml").readAllBytes(),
                StandardCharsets.UTF_8);
        var workflowYamlWithMeta = "name: snap-" + System.currentTimeMillis()
                + "\nversion: \"1.0\"\n" + workflowYaml;

        var flowResponse = FetchUtils.postYaml(flowEngineUrl + "/api/v1/workflows", workflowYamlWithMeta);
        assertEquals(201, flowResponse.statusCode(),
                "Failed to create workflow: " + flowResponse.statusCode() + " " + flowResponse.body());
        var workflow = JSON.MAPPER.readTree(flowResponse.body());
        createdWorkflows.add(workflow.get("id").asText());
        log.info("Workflow created: {}", workflow.get("id").asText());

        // Give the workflow engine time to fully initialize the workflow
        Thread.sleep(2000);

        // One trap transaction per card
        var transactions = new ArrayList<JsonNode>();
        for (var suit : SUITS) {
            for (var rank : RANKS) {
                var body = JSON.MAPPER.createObjectNode();
                body.put("workflowId", workflow.get("id").asText());
                body.put("operation", "play");
                body.putObject("input").put("suit", suit).put("rank", rank);

                var txResponse = FetchUtils.postJson(flowEngineUrl + "/api/v1/transactions",
                        JSON.MAPPER.writeValueAsString(body));
                assertEquals(200, txResponse.statusCode(), txResponse.body());
                transactions.add(JSON.MAPPER.readTree(txResponse.body()));
            }
        }
        log.info("Created {} trap transactions", transactions.size());

        // Wait for all traps to be set
        var trapDeadline = System.currentTimeMillis() + 60_000;
        while (trapsSet.size() < deck.size() && System.currentTimeMillis() < trapDeadline) {
            log.info("Traps set: {}/{}", trapsSet.size(), deck.size());
            Thread.sleep(1000);
        }
        assertEquals(deck.size(), trapsSet.size(), "Not all traps were set");
        log.info("All traps set, starting dealer");

        // Start the dealer event source on a second provider connection
        dealerClient = new WorkflowEngineClient(TestConfig.clientConfig("provider2"));

        var dealt = new AtomicInteger();
        var dealerSource = EventSourceFactory.createEventSource("dealer", (conf, checkpointIn, authRef) -> {
            var alreadyDealt = dealt.get();
            var toDeal = Math.min(ThreadLocalRandom.current().nextInt(1, 10), deck.size() - alreadyDealt);

            if (toDeal <= 0) {
                return EventSourcePollOutput.of(Map.of("dealt", alreadyDealt), List.of());
            }

            var events = deck.subList(alreadyDealt, alreadyDealt + toDeal).stream()
                    .map(card -> EventSourceEvent.of(
                            card.suit() + "-" + card.rank() + "-" + System.nanoTime(),
                            "suit." + card.suit() + ".rank." + card.rank(),
                            card))
                    .toList();

            var total = dealt.addAndGet(toDeal);
            log.info("Dealt {} cards, total: {}/{}", toDeal, total, deck.size());
            return EventSourcePollOutput.of(Map.of("dealt", total), events);
        });

        dealerClient.registerEventSource("dealer", dealerSource);
        dealerClient.connect();
        log.info("Dealer client connected");

        // Give the event source time to register before creating the stream —
        // the engine waits 5s for handler connections
        Thread.sleep(2000);

        var streamBody = JSON.MAPPER.createObjectNode();
        streamBody.put("name", "dealer");
        streamBody.put("started", true);
        var eventSource = streamBody.putObject("eventSource");
        eventSource.put("type", "handler");
        var handlerSource = eventSource.putObject("handler");
        handlerSource.put("name", "dealer");
        handlerSource.put("provider", "provider2");
        handlerSource.putObject("config").put("game", "snap");
        streamBody.putObject("eventProcessor").put("type", "correlation");

        var streamResponse = FetchUtils.putJson(flowEngineUrl + "/api/v1/streams/dealer",
                JSON.MAPPER.writeValueAsString(streamBody));
        assertTrue(streamResponse.statusCode() >= 200 && streamResponse.statusCode() <= 201,
                "Failed to create stream: " + streamResponse.statusCode() + " " + streamResponse.body());
        var stream = JSON.MAPPER.readTree(streamResponse.body());
        createdStreams.add(stream.get("id").asText());
        log.info("Stream created: {}", stream.get("id").asText());

        // Wait for all transactions to reach the snap (or fail) stage
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
                    var stage = state.path("stage").asText();
                    if ("snap".equals(stage) || "fail".equals(stage)) {
                        completed.add(id);
                        log.info("Transaction {} completed in stage {} ({}/{})",
                                id, stage, completed.size(), transactions.size());
                    }
                }
            }
            if (completed.size() < transactions.size()) {
                Thread.sleep(500);
            }
        }

        assertEquals(transactions.size(), completed.size(), "Not all snap transactions completed");
        log.info("All snap transactions completed successfully");
    }
}
