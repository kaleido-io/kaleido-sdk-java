// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Poll function for an event source: given the stream config and the last
 * checkpoint (null on first poll), returns the events read and the new
 * checkpoint. The {@code authRef} (may be null) authorises service-proxy calls
 * made during the poll.
 */
@FunctionalInterface
public interface EventSourcePollFn<CF> {
    EventSourcePollOutput poll(EventSourceConf<CF> conf, JsonNode checkpointIn, String authRef) throws Exception;
}
