// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.handlers;

import io.kaleido.workflowengine.sdk.protocol.WSEventSourceConfig;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceDeleteRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceDeleteResult;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceValidateConfigRequest;
import io.kaleido.workflowengine.sdk.protocol.WSEventSourceValidateConfigResult;
import io.kaleido.workflowengine.sdk.protocol.WSListenerPollRequest;
import io.kaleido.workflowengine.sdk.protocol.WSListenerPollResult;

/**
 * Event source handler interface. Each callback mutates the supplied result
 * object in place rather than returning a value.
 */
public interface EventSource extends Handler {

    /** Poll for events and update the result object. */
    void eventSourcePoll(
            RequestContext reqContext,
            WSEventSourceConfig config,
            WSListenerPollResult result,
            WSListenerPollRequest request) throws Exception;

    /** Validate the event source config. */
    void eventSourceValidateConfig(
            RequestContext reqContext,
            WSEventSourceValidateConfigResult result,
            WSEventSourceValidateConfigRequest request) throws Exception;

    /** Delete the event source. */
    void eventSourceDelete(
            RequestContext reqContext,
            WSEventSourceDeleteResult result,
            WSEventSourceDeleteRequest request) throws Exception;
}
