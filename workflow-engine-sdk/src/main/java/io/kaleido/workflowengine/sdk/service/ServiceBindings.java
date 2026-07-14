// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parser for the {@code service-bindings} config section. Invalid or
 * incomplete entries are skipped with a warning rather than failing the load.
 */
public final class ServiceBindings {
    private ServiceBindings() {}

    private static final Logger log = LoggerFactory.getLogger(ServiceBindings.class);

    /** The {@code bindingType} value selecting the hosted (ws-proxy) transport. */
    public static final String BINDING_TYPE_HOSTED = "hosted";
    /** The {@code bindingType} value selecting the direct HTTP transport. */
    public static final String BINDING_TYPE_NON_HOSTED = "non-hosted";

    public static Map<String, ServiceBindingConfig> parseSection(JsonNode section) {
        var bindings = new LinkedHashMap<String, ServiceBindingConfig>();
        if (section == null || !section.isObject()) {
            return bindings;
        }
        section.fields().forEachRemaining(entry -> {
            var name = entry.getKey();
            var value = entry.getValue();
            if (value == null || !value.isObject()) {
                log.warn("Skipping invalid service binding: {}", name);
                return;
            }
            var serviceType = str(value, "type");
            if (serviceType.isEmpty()) {
                serviceType = name;
            }
            var bindingType = str(value, "bindingType");
            var maxRetries = intOrNull(value, "maxRetries");
            var timeout = intOrNull(value, "timeout");

            if (BINDING_TYPE_HOSTED.equals(bindingType)) {
                var id = str(value, "id");
                if (id.isEmpty()) {
                    log.warn("Skipping hosted binding '{}': missing required 'id' field", name);
                    return;
                }
                bindings.put(name, new ServiceBindingConfig.Hosted(serviceType, id, maxRetries, timeout));
            } else {
                var url = str(value, "url");
                if (url.isEmpty()) {
                    log.warn("Skipping non-hosted binding '{}': missing required 'url' field", name);
                    return;
                }
                var authNode = value.get("auth");
                if (authNode == null || !authNode.isObject()) {
                    log.warn("Skipping non-hosted binding '{}': missing required 'auth' field", name);
                    return;
                }
                bindings.put(name, new ServiceBindingConfig.NonHosted(
                        serviceType, url, parseAuth(authNode), maxRetries, timeout));
            }
        });
        return bindings;
    }

    private static ServiceBindingAuth parseAuth(JsonNode authNode) {
        var authType = str(authNode, "type");
        if (authType.isEmpty()) {
            authType = "basic";
        }
        if ("token".equals(authType)) {
            return new ServiceBindingAuth(authType, null, null,
                    emptyToNull(str(authNode, "token")),
                    emptyToNull(str(authNode, "header")),
                    emptyToNull(str(authNode, "scheme")));
        }
        return new ServiceBindingAuth(authType,
                emptyToNull(str(authNode, "username")),
                emptyToNull(str(authNode, "password")),
                null, null, null);
    }

    private static String str(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isTextual() ? value.asText().trim() : "";
    }

    private static Integer intOrNull(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
