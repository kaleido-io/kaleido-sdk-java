// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

/**
 * Stream identity plus the parsed configuration for an event source stream.
 */
public record EventSourceConf<CF>(
        String streamId,
        String streamName,
        CF config
) {}
