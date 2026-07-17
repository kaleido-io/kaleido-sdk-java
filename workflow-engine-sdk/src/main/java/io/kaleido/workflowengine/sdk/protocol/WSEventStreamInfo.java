// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Identity of an event stream, passed to event source callbacks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WSEventStreamInfo(
        String streamId,
        String streamName
) {}
