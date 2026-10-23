// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kaleido.sdk.core.JSON;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * A response from a service called through {@link ServiceClient}.
 *
 * @param status  the HTTP status
 * @param headers the response headers, read-only, with case-insensitive names
 * @param body    the response body, empty when there was none; do not modify it
 */
public record ServiceResponse(int status, Map<String, String> headers, byte[] body) {

    /** Header names are case-insensitive, however the transport spelled them. */
    public ServiceResponse {
        var caseInsensitive = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            caseInsensitive.putAll(headers);
        }
        headers = Collections.unmodifiableMap(caseInsensitive);
        body = body != null ? body : new byte[0];
    }

    /** The body as UTF-8 text. */
    public String text() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * The body read as JSON into {@code type}, or null when the body is empty.
     *
     * @param type the type to read, e.g. a record, {@code JsonNode} or {@code Map}
     * @param <T>  the type to read
     * @return the body, or null when empty
     */
    public <T> T json(Class<T> type) {
        if (body.length == 0 || type == Void.class) {
            return null;
        }
        try {
            return JSON.MAPPER.readValue(body, type);
        } catch (IOException e) {
            throw new UncheckedIOException("response body is not " + type.getSimpleName() + " JSON", e);
        }
    }

    /**
     * The body read as JSON into a generic type, or null when the body is empty.
     *
     * @param type the type to read, e.g. {@code new TypeReference<List<Item>>() {}}
     * @param <T>  the type to read
     * @return the body, or null when empty
     */
    public <T> T json(TypeReference<T> type) {
        if (body.length == 0) {
            return null;
        }
        try {
            return JSON.MAPPER.readValue(body, type);
        } catch (IOException e) {
            throw new UncheckedIOException("response body is not " + type.getType().getTypeName() + " JSON", e);
        }
    }
}
