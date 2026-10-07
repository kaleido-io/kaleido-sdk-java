// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the {@code service-bindings} config section, which may sit at the top level of the
 * Kaleido config file or under {@code workflow-engine}. Invalid or incomplete entries are
 * skipped with a warning rather than failing the load.
 *
 * <p>To build a client for a non-hosted binding straight from the file, see
 * {@code ServiceClientOptions.fromConfig}.
 */
public final class ServiceBindings {
    private ServiceBindings() {}

    private static final Logger log = LoggerFactory.getLogger(ServiceBindings.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /** Env var naming the Kaleido config file. */
    public static final String KALEIDO_CONFIG_FILE = "KALEIDO_CONFIG_FILE";

    /** The {@code bindingType} value selecting the hosted (ws-proxy) transport. */
    public static final String BINDING_TYPE_HOSTED = "hosted";
    /** The {@code bindingType} value selecting the direct HTTP transport. */
    public static final String BINDING_TYPE_NON_HOSTED = "non-hosted";

    /**
     * Loads the bindings from the file named by {@code KALEIDO_CONFIG_FILE}.
     *
     * @return the bindings by name, empty when the file has none
     * @throws IllegalStateException    when the env var is not set
     * @throws IllegalArgumentException when the file is not valid
     * @throws UncheckedIOException     when the file cannot be read
     */
    public static Map<String, ServiceBindingConfig> load() {
        return load(configFile());
    }

    /**
     * Loads the bindings from a config file.
     *
     * @param file the Kaleido config file
     * @return the bindings by name, empty when the file has none
     * @throws IllegalArgumentException when the file is not valid
     * @throws UncheckedIOException     when the file cannot be read
     */
    public static Map<String, ServiceBindingConfig> load(Path file) {
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read config file " + file + ": " + e.getMessage(), e);
        }
        JsonNode root;
        try {
            root = YAML_MAPPER.readTree(text);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid config file " + file + ": " + e.getMessage(), e);
        }
        // An empty file, or one with only "---" and comments, has no bindings.
        if (root != null && !root.isMissingNode() && !root.isNull() && !root.isObject()) {
            throw new IllegalArgumentException("Invalid config file " + file + ": not a YAML mapping");
        }
        return fromDocument(root);
    }

    /**
     * Reads the bindings from a parsed config document: its top-level {@code service-bindings},
     * else, when that is absent or has no value, the one under {@code workflow-engine}.
     *
     * @param root the parsed document, or null
     * @return the bindings by name, empty when the document has none
     */
    public static Map<String, ServiceBindingConfig> fromDocument(JsonNode root) {
        if (root == null || !root.isObject()) {
            return new LinkedHashMap<>();
        }
        // A top-level key with no value falls back to the nested section.
        var topLevel = root.get("service-bindings");
        return parseSection(topLevel != null && !topLevel.isNull()
                ? topLevel
                : root.path("workflow-engine").get("service-bindings"));
    }

    /**
     * The Kaleido config file, as named by {@code KALEIDO_CONFIG_FILE}.
     *
     * @return the path
     * @throws IllegalStateException when the env var is not set
     */
    public static Path configFile() {
        return configFile(System.getenv(KALEIDO_CONFIG_FILE));
    }

    static Path configFile(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(KALEIDO_CONFIG_FILE + " is not set: name the Kaleido config file");
        }
        return Path.of(path.trim());
    }

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
            // serviceType is the current key; type is still read.
            var serviceType = str(value, "serviceType");
            if (serviceType.isEmpty()) {
                serviceType = str(value, "type");
            }
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
