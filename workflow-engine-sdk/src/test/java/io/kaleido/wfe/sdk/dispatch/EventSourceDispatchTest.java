// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.wfe.sdk.handlers.EventSource;
import io.kaleido.wfe.sdk.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EventSourceDispatchTest {

    @Test
    void pollReturnsCheckpointAndEvents() throws Exception {
        var source = new TrackingEventSource();
        var dispatcher = new WFEDispatcher(
                Map.of("tracker", source), new AtomicReference<>());

        var poll = new WSEventSourcePoll(
                WSMessageType.EVENT_SOURCE_POLL, "poll-1",
                WSHandlerType.EVENT_SOURCE, "tracker",
                null, null, "stream-1", "sid-1", "ck-0", 5, null);

        var json = JSON.MAPPER.writeValueAsString(poll);
        var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, json, sent::add);

        assertEquals(1, sent.size());
        var result = (WSEventSourcePollResult) sent.get(0);
        assertEquals("poll-1", result.id());
        assertEquals("ck-1", result.checkpoint());
        assertNotNull(result.events());
        assertEquals(1, result.events().size());
        assertEquals("topic.item", result.events().get(0).topic());
    }

    @Test
    void validateConfigReturnsValidatedConfig() throws Exception {
        var source = new TrackingEventSource();
        var dispatcher = new WFEDispatcher(
                Map.of("tracker", source), new AtomicReference<>());

        var config = JSON.MAPPER.createObjectNode().put("interval", "5s");
        var validate = new WSEventSourceValidateConfig(
                WSMessageType.EVENT_SOURCE_VALIDATE_CONFIG, "val-1",
                WSHandlerType.EVENT_SOURCE, "tracker",
                null, null, "stream-1", "sid-1", config);

        var json = JSON.MAPPER.writeValueAsString(validate);
        var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, json, sent::add);

        assertEquals(1, sent.size());
        var result = (WSEventSourceValidateConfigResult) sent.get(0);
        assertEquals("val-1", result.id());
        assertNotNull(result.config());
    }

    @Test
    void deleteInvokesHandler() throws Exception {
        var source = new TrackingEventSource();
        var dispatcher = new WFEDispatcher(
                Map.of("tracker", source), new AtomicReference<>());

        var delete = new WSEventSourceDelete(
                WSMessageType.EVENT_SOURCE_DELETE, "del-1",
                WSHandlerType.EVENT_SOURCE, "tracker",
                null, null, "stream-1", "sid-1");

        var json = JSON.MAPPER.writeValueAsString(delete);
        var envelope = JSON.MAPPER.readValue(json, WSEnvelope.class);
        var sent = new ArrayList<>();

        dispatcher.dispatch(envelope, json, sent::add);

        assertEquals(1, sent.size());
        var result = (WSEventSourceDeleteResult) sent.get(0);
        assertEquals("del-1", result.id());
        assertTrue(source.deleteInvoked);
    }

    @Test
    void configChangedTracksStreamId() throws Exception {
        var source = new TrackingEventSource();
        var dispatcher = new WFEDispatcher(
                Map.of("tracker", source), new AtomicReference<>());

        var config1 = new WSEventSourceConfig(
                WSMessageType.EVENT_SOURCE_CONFIG, "cfg-1",
                WSHandlerType.EVENT_SOURCE, "tracker",
                null, null, "stream-a", "sid-a",
                JSON.MAPPER.createObjectNode().put("k", "v1"));

        var json1 = JSON.MAPPER.writeValueAsString(config1);
        var envelope1 = JSON.MAPPER.readValue(json1, WSEnvelope.class);
        var sent = new ArrayList<>();
        dispatcher.dispatch(envelope1, json1, sent::add);

        // Config dispatch does not send a response
        assertTrue(sent.isEmpty());
        assertEquals("sid-a", source.lastConfigStreamId);

        // Second config for different stream
        var config2 = new WSEventSourceConfig(
                WSMessageType.EVENT_SOURCE_CONFIG, "cfg-2",
                WSHandlerType.EVENT_SOURCE, "tracker",
                null, null, "stream-b", "sid-b",
                JSON.MAPPER.createObjectNode().put("k", "v2"));

        var json2 = JSON.MAPPER.writeValueAsString(config2);
        var envelope2 = JSON.MAPPER.readValue(json2, WSEnvelope.class);
        dispatcher.dispatch(envelope2, json2, sent::add);

        assertEquals("sid-b", source.lastConfigStreamId);
        assertEquals(2, source.configChangeCount);
    }

    private static class TrackingEventSource implements EventSource {
        volatile String lastConfigStreamId;
        volatile int configChangeCount;
        volatile boolean deleteInvoked;

        @Override
        public String name() { return "tracker"; }

        @Override
        public WSEventSourceValidateConfigResult validateConfig(WSEventSourceValidateConfig request) {
            return WSEventSourceValidateConfigResult.forRequest(request, request.config());
        }

        @Override
        public WSEventSourcePollResult poll(WSEventSourcePoll request) {
            var event = new ListenerEvent("idem-1", "topic.item",
                    JSON.MAPPER.createObjectNode().put("id", "alpha"));
            return WSEventSourcePollResult.forRequest(request, "ck-1", List.of(event));
        }

        @Override
        public WSEventSourceDeleteResult delete(WSEventSourceDelete request) {
            this.deleteInvoked = true;
            return WSEventSourceDeleteResult.forRequest(request);
        }

        @Override
        public void onConfigChanged(WSEventSourceConfig request) {
            this.lastConfigStreamId = request.streamId();
            this.configChangeCount++;
        }
    }
}
