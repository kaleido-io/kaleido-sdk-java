// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

/**
 * Options for constructing a typed service client, resolved from a named
 * service binding. Provider code never constructs transports directly — it
 * obtains {@code ServiceClientOptions} from
 * {@code WorkflowEngineClient.getServiceClientOptions()} and passes them to a
 * typed client constructor.
 */
public sealed interface ServiceClientOptions {

    /** Direct HTTP transport resolved from a non-hosted binding. */
    record Http(
            String url,
            ServiceBindingAuth auth,
            Integer maxRetries,
            Integer timeout
    ) implements ServiceClientOptions {}

    /** WebSocket-proxy transport resolved from a hosted binding. */
    record WsProxy(
            WSProxyAdapter wsProxy,
            String serviceType,
            String id,
            String authRef
    ) implements ServiceClientOptions {}
}
