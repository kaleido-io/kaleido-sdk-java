// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample.componenttest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.kaleido.workflowengine.sdk.client.WorkflowEngineClient;
import io.kaleido.workflowengine.sdk.factories.EventSourceEvent;
import io.kaleido.workflowengine.sdk.factories.EventSourceFactory;
import io.kaleido.workflowengine.sdk.factories.EventSourcePollOutput;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sample.EchoEventProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the sample's real {@link EchoEventProcessor} receives a genuine
 * event batch dispatched by a live workflow engine: registers {@code echo}
 * alongside a throwaway {@code feeder} event source on the same provider
 * connection, drives a stream binding {@code feeder -> echo}
 * ({@code eventProcessor.type = "handler"}), and asserts the batch was
 * logged by attaching a Logback {@link ListAppender} to echo's logger — the
 * shipped handler is exercised unmodified.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EchoEventProcessorTest {

    private static final String TOPIC = "sample.echo.componenttest";

    private final String flowEngineUrl = TestConfig.flowEngineUrl();
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    private String streamId;
    private WorkflowEngineClient client;

    @BeforeAll
    void setUp() throws Exception {
        var echoLogger = (Logger) LoggerFactory.getLogger(EchoEventProcessor.class);
        logAppender.start();
        echoLogger.addAppender(logAppender);

        client = new WorkflowEngineClient(TestConfig.clientConfig("echo-sample"));
        client.registerHandler(new EchoEventProcessor());

        var feeder = EventSourceFactory.createEventSource("feeder", (conf, checkpointIn, authRef) -> {
            if (checkpointIn != null && checkpointIn.path("fed").asBoolean(false)) {
                return EventSourcePollOutput.of(Map.of("fed", true), List.of());
            }
            var events = List.of(
                    EventSourceEvent.of("echo-1", TOPIC, Map.of("greeting", "hello")),
                    EventSourceEvent.of("echo-2", TOPIC, Map.of("greeting", "world")));
            return EventSourcePollOutput.of(Map.of("fed", true), events);
        });
        client.registerHandler(feeder);

        client.connect();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (client != null) {
            client.disconnect();
        }
        if (streamId != null) {
            FetchUtils.delete(flowEngineUrl + "/api/v1/streams/" + streamId);
        }
        var echoLogger = (Logger) LoggerFactory.getLogger(EchoEventProcessor.class);
        echoLogger.detachAppender(logAppender);
    }

    @Test
    @Timeout(60)
    void echoReceivesADispatchedEventBatch() throws Exception {
        // The engine waits a few seconds for handler connections to settle
        // before it will accept a stream bound to them.
        Thread.sleep(2000);

        var streamBody = JSON.MAPPER.createObjectNode();
        streamBody.put("name", "echo-componenttest");
        streamBody.put("started", true);
        var eventSource = streamBody.putObject("eventSource");
        eventSource.put("type", "handler");
        var handlerSource = eventSource.putObject("handler");
        handlerSource.put("name", "feeder");
        handlerSource.put("provider", "echo-sample");
        handlerSource.putObject("config");
        var eventProcessor = streamBody.putObject("eventProcessor");
        eventProcessor.put("type", "handler");
        var handlerProcessor = eventProcessor.putObject("handler");
        handlerProcessor.put("name", "echo");
        handlerProcessor.put("provider", "echo-sample");

        var streamResponse = FetchUtils.putJson(flowEngineUrl + "/api/v1/streams/echo-componenttest",
                JSON.MAPPER.writeValueAsString(streamBody));
        assertTrue(streamResponse.statusCode() >= 200 && streamResponse.statusCode() <= 201,
                "Failed to create stream: " + streamResponse.statusCode() + " " + streamResponse.body());
        var stream = JSON.MAPPER.readTree(streamResponse.body());
        streamId = stream.get("id").asText();

        var deadline = System.currentTimeMillis() + 30_000;
        var sawBatch = false;
        while (System.currentTimeMillis() < deadline) {
            sawBatch = logAppender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains(TOPIC));
            if (sawBatch) {
                break;
            }
            Thread.sleep(200);
        }

        assertTrue(sawBatch, "echo never logged an event carrying topic " + TOPIC);
    }
}
