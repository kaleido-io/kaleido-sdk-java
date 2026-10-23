// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import io.kaleido.sdk.core.config.ServiceBindingAuth;
import io.kaleido.sdk.core.config.ServiceBindingConfig;
import io.kaleido.sdk.core.config.ServiceBindings;

import java.nio.file.Path;
import java.util.Map;

/**
 * How a {@link ServiceClient} reaches its service. Build {@link Http} directly, or resolve a
 * service binding: {@link #forBinding} (the workflow engine SDK's
 * {@code getServiceClientOptions()} calls it), or {@link #fromConfig} outside a provider.
 */
public sealed interface ServiceClientOptions {

    /** Direct HTTP to a URL, given directly or resolved from a non-hosted binding. */
    record Http(
            String url,
            ServiceBindingAuth auth,
            Integer maxRetries,
            Integer timeout
    ) implements ServiceClientOptions {}

    /** Through the provider-proxy, resolved from a hosted binding. */
    record WsProxy(
            ServiceProxy wsProxy,
            String serviceType,
            String id,
            String authRef
    ) implements ServiceClientOptions {}

    /**
     * Resolves a binding to the options that reach it.
     *
     * @param binding the binding
     * @param proxy   the provider-proxy, needed only for a hosted binding
     * @param authRef the request's auth reference, so the call runs as its user, or null
     * @return the options
     * @throws IllegalStateException when the binding is hosted and there is no proxy
     */
    static ServiceClientOptions forBinding(ServiceBindingConfig binding, ServiceProxy proxy, String authRef) {
        return switch (binding) {
            case ServiceBindingConfig.Hosted hosted -> {
                if (proxy == null) {
                    throw new IllegalStateException("Service binding of type '" + hosted.type()
                            + "' is hosted and needs a provider-proxy connection to reach it");
                }
                yield new WsProxy(proxy, hosted.type(), hosted.id(), authRef);
            }
            case ServiceBindingConfig.NonHosted nonHosted -> new Http(
                    nonHosted.url(), nonHosted.auth(), nonHosted.maxRetries(), nonHosted.timeout());
        };
    }

    /**
     * Resolves a non-hosted binding from the Kaleido config file named by
     * {@code KALEIDO_CONFIG_FILE}, for a client running without a provider.
     *
     * @param name the binding name
     * @return the options
     * @throws IllegalStateException    when the env var is not set, or the binding is hosted
     * @throws IllegalArgumentException when there is no such binding, or the file is not valid
     * @throws java.io.UncheckedIOException when the file cannot be read
     */
    static ServiceClientOptions fromConfig(String name) {
        return fromConfig(name, ServiceBindings.configFile());
    }

    /**
     * Resolves a non-hosted binding from a Kaleido config file. A hosted binding is reached
     * through the provider-proxy, so it needs a connected workflow engine client instead.
     *
     * @param name the binding name
     * @param file the Kaleido config file
     * @return the options
     * @throws IllegalStateException    when the binding is hosted
     * @throws IllegalArgumentException when there is no such binding, or the file is not valid
     * @throws java.io.UncheckedIOException when the file cannot be read
     */
    static ServiceClientOptions fromConfig(String name, Path file) {
        return named(name, ServiceBindings.load(file), file);
    }

    private static ServiceClientOptions named(String name, Map<String, ServiceBindingConfig> bindings, Path file) {
        var binding = bindings.get(name);
        if (binding == null) {
            var available = String.join(", ", bindings.keySet());
            throw new IllegalArgumentException("Service binding '" + name + "' not found in " + file
                    + ". Available: " + (available.isEmpty() ? "(none)" : available));
        }
        if (binding instanceof ServiceBindingConfig.Hosted) {
            throw new IllegalStateException("Service binding '" + name + "' is hosted and is reached through the "
                    + "provider-proxy: get its options from a connected workflow engine client instead");
        }
        return forBinding(binding, null, null);
    }
}
