// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceConfig;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceDeleteRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceDeleteResult;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceValidateConfigRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceValidateConfigResult;
import io.kaleido.workflowengine.sdk.protocol.WSListenerPollRequest;
import io.kaleido.workflowengine.sdk.protocol.WSListenerPollResult;
import io.kaleido.workflowengine.sdk.protocol.WSMessageType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventSourceFactoryTest {

    private static WSEventSourceConfig config(String streamId, JsonNode config) {
        return new WSEventSourceConfig(WSMessageType.EVENT_SOURCE_CONFIG, "cfg-1", null,
                null, "source-1", null, null, "stream-name", streamId, config);
    }

    private static WSListenerPollRequest pollRequest(String streamId, JsonNode checkpoint) {
        return new WSListenerPollRequest(WSMessageType.EVENT_SOURCE_POLL, "poll-1", null,
                null, "source-1", null, null, "stream-name", streamId, null, checkpoint, "auth-1");
    }

    @Test
    void pollSuccessMapsEventsAndCheckpoint() throws Exception {
        var source = EventSourceFactory.createEventSource("source-1", (conf, checkpointIn, authRef) -> {
            assertEquals("stream-1", conf.streamId());
            assertNull(checkpointIn);
            assertEquals("auth-1", authRef);
            return EventSourcePollOutput.of(
                    java.util.Map.of("dealt", 5),
                    List.of(EventSourceEvent.of("key-1", "topic-a", java.util.Map.of("card", "ace"))));
        });

        var result = new WSListenerPollResult();
        source.eventSourcePoll(null, config("stream-1", null), result, pollRequest("stream-1", null));

        assertNull(result.getError());
        assertEquals(1, result.getEvents().size());
        assertEquals("key-1", result.getEvents().get(0).idempotencyKey());
        assertEquals("topic-a", result.getEvents().get(0).topic());
        assertEquals(5, result.getCheckpoint().get("dealt").asInt());
    }

    @Test
    void pollFailureSetsErrorAndDoesNotAdvanceCheckpoint() {
        var source = EventSourceFactory.createEventSource("source-1", (conf, checkpointIn, authRef) -> {
            throw new RuntimeException("poll exploded");
        });

        var result = new WSListenerPollResult();
        assertDoesNotThrow(() ->
                source.eventSourcePoll(null, config("stream-1", null), result, pollRequest("stream-1", null)));

        assertEquals("poll exploded", result.getError());
        assertNull(result.getCheckpoint());
        assertTrue(result.getEvents().isEmpty());
    }

    @Test
    void configParsedOncePerStreamAndClearedOnDelete() throws Exception {
        record ParsedConfig(String game) {}
        var parseCount = new AtomicInteger();

        var source = EventSourceFactory.createEventSource("source-1",
                        (EventSourcePollFn<ParsedConfig>) (conf, checkpointIn, authRef) -> {
                            assertEquals("snap", conf.config().game());
                            return EventSourcePollOutput.of(java.util.Map.of(), List.of());
                        })
                .withConfigParser((info, configData) -> {
                    parseCount.incrementAndGet();
                    return new ParsedConfig(configData.get("game").asText());
                });

        var configMessage = config("stream-1", JSON.MAPPER.createObjectNode().put("game", "snap"));

        var result = new WSListenerPollResult();
        source.eventSourcePoll(null, configMessage, result, pollRequest("stream-1", null));
        source.eventSourcePoll(null, configMessage, result, pollRequest("stream-1", null));
        assertEquals(1, parseCount.get());

        // Delete clears the cache; the next poll re-parses
        var deleteRequest = new WSEventSourceDeleteRequest(WSMessageType.EVENT_SOURCE_DELETE, "del-1",
                null, null, "source-1", null, null, "stream-name", "stream-1");
        source.eventSourceDelete(null, new WSEventSourceDeleteResult(), deleteRequest);

        source.eventSourcePoll(null, configMessage, result, pollRequest("stream-1", null));
        assertEquals(2, parseCount.get());
    }

    @Test
    void deleteFnInvokedAndErrorsCaptured() throws Exception {
        var deleted = new AtomicInteger();
        var source = EventSourceFactory.createEventSource("source-1",
                        (conf, checkpointIn, authRef) -> EventSourcePollOutput.of(java.util.Map.of(), List.of()))
                .withDeleteFn(info -> {
                    deleted.incrementAndGet();
                    if (deleted.get() > 1) {
                        throw new RuntimeException("delete failed");
                    }
                });

        var deleteRequest = new WSEventSourceDeleteRequest(WSMessageType.EVENT_SOURCE_DELETE, "del-1",
                null, null, "source-1", null, null, "stream-name", "stream-1");

        var ok = new WSEventSourceDeleteResult();
        source.eventSourceDelete(null, ok, deleteRequest);
        assertNull(ok.getError());

        var failed = new WSEventSourceDeleteResult();
        source.eventSourceDelete(null, failed, deleteRequest);
        assertEquals("delete failed", failed.getError());
    }

    @Test
    void validateConfigBuildsInitialCheckpoint() throws Exception {
        var source = EventSourceFactory.createEventSource("source-1",
                        (conf, checkpointIn, authRef) -> EventSourcePollOutput.of(java.util.Map.of(), List.of()))
                .withInitialCheckpoint(config -> java.util.Map.of("offset", 100));

        var request = new WSEventSourceValidateConfigRequest(WSMessageType.EVENT_SOURCE_VALIDATE_CONFIG,
                "val-1", null, null, "source-1", null, null, "stream-name", "stream-1",
                JSON.MAPPER.createObjectNode());
        var result = new WSEventSourceValidateConfigResult();
        source.eventSourceValidateConfig(null, result, request);

        assertNull(result.getError());
        assertEquals(100, result.getInitialCheckpoint().get("offset").asInt());
    }

    @Test
    void validateConfigParserFailureSetsError() throws Exception {
        var source = EventSourceFactory.createEventSource("source-1", String.class,
                (conf, checkpointIn, authRef) -> EventSourcePollOutput.of(java.util.Map.of(), List.of()))
                .withConfigParser((info, configData) -> {
                    throw new IllegalArgumentException("bad config");
                });

        var request = new WSEventSourceValidateConfigRequest(WSMessageType.EVENT_SOURCE_VALIDATE_CONFIG,
                "val-1", null, null, "source-1", null, null, "stream-name", "stream-1",
                JSON.MAPPER.createObjectNode());
        var result = new WSEventSourceValidateConfigResult();
        source.eventSourceValidateConfig(null, result, request);

        assertEquals("bad config", result.getError());
    }

    @Test
    void initAndCloseHooks() throws Exception {
        var initialized = new AtomicInteger();
        var closed = new AtomicInteger();
        var source = EventSourceFactory.createEventSource("source-1",
                        (conf, checkpointIn, authRef) -> EventSourcePollOutput.of(java.util.Map.of(), List.of()))
                .withInitFn(api -> initialized.incrementAndGet())
                .withCloseFn(closed::incrementAndGet);

        source.init(null);
        source.close();
        assertEquals(1, initialized.get());
        assertEquals(1, closed.get());
    }
}
