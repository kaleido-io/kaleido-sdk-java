// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import com.fasterxml.jackson.core.type.TypeReference;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Calls a service with JSON bodies, however its {@link ServiceClientOptions} reach it:
 * straight to a URL with its auth, or, for a hosted binding, through the provider-proxy over
 * the provider's WebSocket as the user behind the request's {@code authRef}. Swapping one
 * for the other needs no code change.
 *
 * <p>Use it directly, or extend it into a typed client for one service:
 *
 * <pre>{@code
 * class AssetManagerClient extends ServiceClient {
 *     AssetManagerClient(ServiceClientOptions options) { super(options); }
 *
 *     Asset asset(String id) { return get("/api/v1/assets/" + id, Asset.class); }
 * }
 *
 * var assets = new AssetManagerClient(client.getServiceClientOptions("asset-manager", tx.authRef()));
 * }</pre>
 *
 * <p>Paths are relative to the service's base URL. A status other than 2xx throws
 * {@link ServiceClientException}.
 */
public class ServiceClient {

    private final ServiceTransport transport;

    public ServiceClient(ServiceClientOptions options) {
        this(switch (options) {
            case ServiceClientOptions.WsProxy proxy -> new WsProxyServiceTransport(proxy);
            case ServiceClientOptions.Http http -> new HttpServiceTransport(http);
        });
    }

    ServiceClient(ServiceTransport transport) {
        this.transport = transport;
    }

    /**
     * GETs {@code path} and reads the response as {@code type}.
     *
     * @param path the path, relative to the service's base URL
     * @param type the response type, e.g. a record, {@code JsonNode} or {@code Map}
     * @param <T>  the response type
     * @return the response body, or null when empty
     */
    public <T> T get(String path, Class<T> type) {
        return get(path, Map.of(), type);
    }

    /**
     * GETs {@code path} with query {@code params} (an {@code Iterable} value repeats the key).
     *
     * @param path   the path, relative to the service's base URL
     * @param params query parameters; null values are skipped
     * @param type   the response type
     * @param <T>    the response type
     * @return the response body, or null when empty
     */
    public <T> T get(String path, Map<String, ?> params, Class<T> type) {
        return request("GET", withQuery(path, params), null).json(type);
    }

    /**
     * GETs {@code path} and reads the response as a generic type, such as a page of items.
     *
     * @param path   the path, relative to the service's base URL
     * @param params query parameters; null values are skipped
     * @param type   the response type, e.g. {@code new TypeReference<Page<Item>>() {}}
     * @param <T>    the response type
     * @return the response body, or null when empty
     */
    public <T> T get(String path, Map<String, ?> params, TypeReference<T> type) {
        return request("GET", withQuery(path, params), null).json(type);
    }

    /**
     * GETs {@code path}, with a 404 as empty rather than an error.
     *
     * @param path   the path, relative to the service's base URL
     * @param params query parameters; null values are skipped
     * @param type   the response type
     * @param <T>    the response type
     * @return the response body, or empty on 404
     */
    public <T> Optional<T> find(String path, Map<String, ?> params, Class<T> type) {
        var response = transport.send("GET", withQuery(path, params), null);
        if (response.status() == 404) {
            return Optional.empty();
        }
        return Optional.ofNullable(checked("GET", path, response).json(type));
    }

    /**
     * POSTs {@code body} as JSON.
     *
     * @param path the path, relative to the service's base URL
     * @param body the request body, or null for none
     * @param type the response type ({@code Void.class} to ignore it)
     * @param <T>  the response type
     * @return the response body, or null when empty
     */
    public <T> T post(String path, Object body, Class<T> type) {
        return request("POST", path, body).json(type);
    }

    /**
     * PUTs {@code body} as JSON.
     *
     * @param path the path, relative to the service's base URL
     * @param body the request body, or null for none
     * @param type the response type ({@code Void.class} to ignore it)
     * @param <T>  the response type
     * @return the response body, or null when empty
     */
    public <T> T put(String path, Object body, Class<T> type) {
        return request("PUT", path, body).json(type);
    }

    /**
     * PATCHes {@code body} as JSON.
     *
     * @param path the path, relative to the service's base URL
     * @param body the request body, or null for none
     * @param type the response type ({@code Void.class} to ignore it)
     * @param <T>  the response type
     * @return the response body, or null when empty
     */
    public <T> T patch(String path, Object body, Class<T> type) {
        return request("PATCH", path, body).json(type);
    }

    /**
     * DELETEs {@code path}.
     *
     * @param path the path, relative to the service's base URL
     */
    public void delete(String path) {
        request("DELETE", path, null);
    }

    /**
     * Sends any request, for a method or response handling the helpers do not cover.
     *
     * @param method the HTTP method
     * @param path   the path and query, relative to the service's base URL
     * @param body   the JSON body, or null for none
     * @return the response, whose status is 2xx
     */
    public ServiceResponse request(String method, String path, Object body) {
        return checked(method, path, transport.send(method, path, body));
    }

    /** How much of an error body goes in the exception message; {@code body()} has all of it. */
    static final int MESSAGE_BODY_LIMIT = 1024;

    private static ServiceResponse checked(String method, String path, ServiceResponse response) {
        if (response.status() < 200 || response.status() >= 300) {
            var text = response.text();
            var shown = text.length() > MESSAGE_BODY_LIMIT ? text.substring(0, MESSAGE_BODY_LIMIT) + "..." : text;
            throw new ServiceClientException(method + " " + path + ": HTTP " + response.status() + ": " + shown,
                    response.status(), text, null);
        }
        return response;
    }

    static String withQuery(String path, Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return path;
        }
        var pairs = new ArrayList<String>();
        params.forEach((key, value) -> {
            if (value instanceof Iterable<?> values) {
                values.forEach(v -> addPair(pairs, key, v));
            } else {
                addPair(pairs, key, value);
            }
        });
        if (pairs.isEmpty()) {
            return path;
        }
        return path + (path.contains("?") ? "&" : "?") + String.join("&", pairs);
    }

    private static void addPair(ArrayList<String> pairs, String key, Object value) {
        if (value != null) {
            pairs.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
        }
    }
}
