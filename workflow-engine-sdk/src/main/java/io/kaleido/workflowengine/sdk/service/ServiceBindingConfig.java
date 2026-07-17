// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

/**
 * Configuration for a single named service binding.
 *
 * <ul>
 *   <li>{@link Hosted} — routes requests through the WebSocket proxy transport.
 *       The {@code id} identifies the service instance on the proxy side
 *       (which maps to the actual service URL). No URL or auth needed.</li>
 *   <li>{@link NonHosted} — direct HTTP transport using the provided
 *       {@code url} and {@code auth}.</li>
 * </ul>
 */
public sealed interface ServiceBindingConfig {

    /** Routing key identifying the target service type (e.g. 'asset-manager', 'key-manager', 'apigw'). */
    String type();

    Integer maxRetries();

    Integer timeout();

    record Hosted(
            String type,
            String id,
            Integer maxRetries,
            Integer timeout
    ) implements ServiceBindingConfig {}

    record NonHosted(
            String type,
            String url,
            ServiceBindingAuth auth,
            Integer maxRetries,
            Integer timeout
    ) implements ServiceBindingConfig {}
}
