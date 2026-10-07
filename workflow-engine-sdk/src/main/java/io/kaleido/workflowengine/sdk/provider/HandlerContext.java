// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.provider;

import io.kaleido.sdk.core.http.ServiceClient;
import io.kaleido.sdk.core.http.ServiceClientOptions;

import java.nio.file.Path;
import java.util.Map;

/**
 * What a runtime gives a {@link HandlerFactory} for one handler: its name, its own config,
 * secrets and files, and the provider's service bindings.
 */
public interface HandlerContext {

    /**
     * The name the handler is registered under, which workflows and streams route on.
     *
     * @return the handler name
     */
    String name();

    /**
     * The provider's name with the workflow engine.
     *
     * @return the provider name
     */
    String providerName();

    /**
     * The handler's config object, read-only and empty when it has none. Values are as parsed
     * from the config: strings, numbers, booleans, lists, maps and nulls.
     *
     * @return the config
     */
    Map<String, Object> config();

    /**
     * The handler's config bound to {@code type}, for example a record. Binding is strict, so a
     * misspelt key or a missing primitive fails when the factory runs rather than defaulting.
     *
     * @param type the type to bind to
     * @param <T>  the type to bind to
     * @return the bound config
     * @throws IllegalArgumentException when the config does not fit {@code type}
     */
    default <T> T config(Class<T> type) {
        return HandlerConfigs.MAPPER.convertValue(config(), type);
    }

    /**
     * The handler's secrets by name, read-only and empty when it has none. The runtime supplies
     * them separately from {@link #config()}, so secrets stay out of it.
     *
     * @return the secrets
     */
    Map<String, String> secrets();

    /**
     * The value of the secret named {@code name}.
     *
     * @param name the secret's name in the handler's config
     * @return the value
     * @throws IllegalArgumentException when the handler has no such secret
     */
    default String secret(String name) {
        var value = secrets().get(name);
        if (value == null) {
            throw new IllegalArgumentException("handler '" + name() + "' has no secret '" + name + "'");
        }
        return value;
    }

    /**
     * The handler's files by name, read-only and empty when it has none: the path of each
     * file the runtime provides.
     *
     * @return the file paths
     */
    Map<String, Path> files();

    /**
     * The path of the file named {@code name}.
     *
     * @param name the file's name in the handler's config
     * @return the path
     * @throws IllegalArgumentException when the handler has no such file
     */
    default Path file(String name) {
        var path = files().get(name);
        if (path == null) {
            throw new IllegalArgumentException("handler '" + name() + "' has no file '" + name + "'");
        }
        return path;
    }

    /**
     * The transport options for the service binding named {@code binding}.
     *
     * @param binding the binding name in the provider's config
     * @param authRef the request's auth reference, so a hosted binding calls the service as the
     *                user behind it; null for calls made outside a request
     * @return the options
     */
    ServiceClientOptions serviceClientOptions(String binding, String authRef);

    /**
     * A client for the service binding named {@code binding}.
     *
     * @param binding the binding name in the provider's config
     * @param authRef the request's auth reference, or null
     * @return the client
     */
    default ServiceClient serviceClient(String binding, String authRef) {
        return new ServiceClient(serviceClientOptions(binding, authRef));
    }
}
