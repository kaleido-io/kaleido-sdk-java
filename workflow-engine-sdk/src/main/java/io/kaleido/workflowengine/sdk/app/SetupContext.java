// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.app;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.handlers.CancellationSignal;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.service.ServiceClientOptions;

import java.util.function.Function;

/**
 * Context injected into handler setup hooks.
 *
 * <p>Provides access to per-provider custom config, provider identity, and
 * service bindings. Constructed by the workflow engine client — application
 * code receives it in {@code setup()} callbacks and passes it to typed service
 * clients.
 */
public class SetupContext {

    private final JsonNode config;
    private final String providerName;
    private final String handlerName;
    private final CancellationSignal signal;
    private final Function<String, ServiceClientOptions> serviceClientOptionsResolver;

    public SetupContext(JsonNode config, String providerName, String handlerName,
                        CancellationSignal signal,
                        Function<String, ServiceClientOptions> serviceClientOptionsResolver) {
        this.config = config;
        this.providerName = providerName;
        this.handlerName = handlerName;
        this.signal = signal;
        this.serviceClientOptionsResolver = serviceClientOptionsResolver;
    }

    protected SetupContext(SetupContext other) {
        this(other.config, other.providerName, other.handlerName, other.signal,
                other.serviceClientOptionsResolver);
    }

    /** The provider-specific custom config, or null when none was supplied. */
    public JsonNode config() {
        return config;
    }

    /** Deserialize the provider-specific custom config into a typed object. */
    public <T> T config(Class<T> type) throws Exception {
        return config == null ? null : JSON.MAPPER.treeToValue(config, type);
    }

    public String providerName() {
        return providerName;
    }

    public String handlerName() {
        return handlerName;
    }

    public CancellationSignal signal() {
        return signal;
    }

    /**
     * Resolve a named service binding from the provider config. The returned
     * options can be passed directly to a typed client constructor.
     */
    public ServiceClientOptions getServiceClientOptions(String bindingName) {
        return serviceClientOptionsResolver.apply(bindingName);
    }
}
