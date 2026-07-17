// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.handlers.EngineAPI;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.ListenerEvent;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceConfig;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceDeleteRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceDeleteResult;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceValidateConfigRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceValidateConfigResult;
import io.kaleido.workflowengine.sdk.protocol.WSEventStreamInfo;
import io.kaleido.workflowengine.sdk.protocol.WSListenerPollRequest;
import io.kaleido.workflowengine.sdk.protocol.WSListenerPollResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for event sources built from a poll function, with optional
 * lifecycle hooks configured through the returned {@link EventSourceBuilder}.
 */
public final class EventSourceFactory {
    private EventSourceFactory() {}

    private static final Logger log = LoggerFactory.getLogger(EventSourceFactory.class);

    /**
     * Create a new event source with a poll function. Without a config parser
     * (see {@link EventSourceBuilder#withConfigParser}), the stream config
     * passed to the poll function is the raw config {@link JsonNode}.
     *
     * @param name   handler name to register with the workflow engine
     * @param pollFn function that polls for new events
     */
    public static <CF> EventSourceBuilder<CF> createEventSource(String name, EventSourcePollFn<CF> pollFn) {
        return new EventSourceBase<>(name, pollFn);
    }

    /**
     * Create a new event source with a poll function and a typed stream
     * config — configure the parser with
     * {@link EventSourceBuilder#withConfigParser}.
     */
    public static <CF> EventSourceBuilder<CF> createEventSource(
            String name, Class<CF> configType, EventSourcePollFn<CF> pollFn) {
        return new EventSourceBase<CF>(name, pollFn)
                .withConfigParser((info, config) ->
                        config == null ? null : JSON.MAPPER.treeToValue(config, configType));
    }

    private static final class EventSourceBase<CF> implements EventSourceBuilder<CF> {

        private final String name;
        private final EventSourcePollFn<CF> pollFn;
        private EventSourceDeleteFn deleteFn;
        private EventSourceConfigParserFn<CF> configParserFn;
        private EventSourceInitialCheckpointFn<CF> initialCheckpointFn;
        private HandlerInitFn initFn;
        private Runnable closeFn;

        private final Map<String, EventSourceConf<CF>> confs = new ConcurrentHashMap<>();

        private EventSourceBase(String name, EventSourcePollFn<CF> pollFn) {
            this.name = name;
            this.pollFn = pollFn;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public EventSourceBuilder<CF> withDeleteFn(EventSourceDeleteFn deleteFn) {
            this.deleteFn = deleteFn;
            return this;
        }

        @Override
        public EventSourceBuilder<CF> withConfigParser(EventSourceConfigParserFn<CF> parserFn) {
            this.configParserFn = parserFn;
            return this;
        }

        @Override
        public EventSourceBuilder<CF> withInitialCheckpoint(EventSourceInitialCheckpointFn<CF> buildFn) {
            this.initialCheckpointFn = buildFn;
            return this;
        }

        @Override
        public EventSourceBuilder<CF> withInitFn(HandlerInitFn initFn) {
            this.initFn = initFn;
            return this;
        }

        @Override
        public EventSourceBuilder<CF> withCloseFn(Runnable closeFn) {
            this.closeFn = closeFn;
            return this;
        }

        @Override
        public void init(EngineAPI engineAPI) throws Exception {
            if (initFn != null) {
                initFn.init(engineAPI);
            }
        }

        @Override
        public void close() {
            if (closeFn != null) {
                closeFn.run();
            }
            confs.clear();
        }

        @SuppressWarnings("unchecked")
        private CF buildConf(WSEventStreamInfo info, JsonNode configData) throws Exception {
            if (configParserFn != null) {
                return configParserFn.parse(info, configData);
            }
            return (CF) configData;
        }

        private EventSourceConf<CF> buildAndCacheConf(
                WSEventSourceConfig config, WSListenerPollRequest request) throws Exception {
            var info = new WSEventStreamInfo(request.streamId(), request.streamName());
            var parsedConfig = buildConf(info, config.config());
            var esConf = new EventSourceConf<>(info.streamId(), info.streamName(), parsedConfig);
            confs.put(request.streamId(), esConf);
            return esConf;
        }

        /**
         * Poll for events. Failures set {@code result.error} rather than
         * propagating, so one bad poll cannot take down the dispatch loop, and
         * the checkpoint is not advanced.
         */
        @Override
        public void eventSourcePoll(RequestContext reqContext, WSEventSourceConfig config,
                                    WSListenerPollResult result, WSListenerPollRequest request) {
            try {
                var esConf = confs.get(request.streamId());
                if (esConf == null) {
                    esConf = buildAndCacheConf(config, request);
                }

                var pollOutput = pollFn.poll(esConf, request.checkpoint(), request.authRef());

                result.setEvents(pollOutput.events().stream()
                        .map(evt -> new ListenerEvent(evt.idempotencyKey(), evt.topic(), evt.data()))
                        .toList());
                result.setCheckpoint(pollOutput.checkpoint());
            } catch (Exception e) {
                log.error("Poll failed", e);
                result.setError(e.getMessage());
            }
        }

        @Override
        public void eventSourceValidateConfig(RequestContext reqContext,
                                              WSEventSourceValidateConfigResult result,
                                              WSEventSourceValidateConfigRequest request) {
            try {
                var parsedConfig = buildConf(
                        new WSEventStreamInfo(request.streamId(), request.streamName()),
                        request.config());
                if (initialCheckpointFn != null) {
                    var initialCheckpoint = initialCheckpointFn.buildInitialCheckpoint(parsedConfig);
                    result.setInitialCheckpoint(JSON.MAPPER.valueToTree(initialCheckpoint));
                }
            } catch (Exception e) {
                log.error("Failed to validate config", e);
                result.setError(e.getMessage());
            }
        }

        @Override
        public void eventSourceDelete(RequestContext reqContext,
                                      WSEventSourceDeleteResult result,
                                      WSEventSourceDeleteRequest request) {
            try {
                if (deleteFn != null) {
                    deleteFn.delete(new WSEventStreamInfo(request.streamId(), request.streamName()));
                }
                confs.remove(request.streamId());
            } catch (Exception e) {
                log.error("Delete failed", e);
                result.setError(e.getMessage());
            }
        }
    }
}
