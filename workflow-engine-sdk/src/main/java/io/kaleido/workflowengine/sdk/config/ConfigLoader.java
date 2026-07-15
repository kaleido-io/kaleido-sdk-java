// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kaleido.workflowengine.sdk.errors.SDKErrors;
import io.kaleido.workflowengine.sdk.service.ServiceBindingConfig;
import io.kaleido.workflowengine.sdk.service.ServiceBindings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads {@link ClientConfig} from the Kaleido-managed YAML config file.
 *
 * <p>File path resolution order: an explicit path argument, then the
 * {@code KALEIDO_CONFIG_FILE} env var, then the legacy {@code WFE_CONFIG_FILE}
 * env var. Only the root key {@code workflow-engine} is read for connection
 * settings; {@code service-bindings} may appear at the top level or nested
 * under {@code workflow-engine}.
 *
 * <p>Provider-specific custom config is attached during load: the file named by
 * the {@code CONFIG_FILE} env var (default {@code ./config/provider-config.yaml}),
 * falling back to the {@code config:} key of the Kaleido config file.
 */
public final class ConfigLoader {
    private ConfigLoader() {}

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d+)(ms|s|m|h)$");

    /** Env var naming the Kaleido-managed config file (service bindings etc.). */
    public static final String KALEIDO_CONFIG_FILE = "KALEIDO_CONFIG_FILE";
    /** @deprecated Use {@link #KALEIDO_CONFIG_FILE}. */
    @Deprecated
    public static final String WFE_CONFIG_FILE = "WFE_CONFIG_FILE";
    /** Env var naming the developer-managed provider-specific config file. */
    public static final String CONFIG_FILE = "CONFIG_FILE";

    static final String DEFAULT_PROVIDER_CONFIG_PATH = "./config/provider-config.yaml";

    /**
     * Load client config from the file named by {@code KALEIDO_CONFIG_FILE}
     * (or the legacy {@code WFE_CONFIG_FILE}).
     */
    public static ClientConfig fromEnv() {
        var configPath = resolveConfigPath(null);
        if (configPath == null) {
            throw SDKErrors.newError(SDKErrors.MSG_CONFIG_FILE_NOT_SET, KALEIDO_CONFIG_FILE);
        }
        return load(Path.of(configPath));
    }

    /**
     * Load client config from a YAML file.
     */
    public static ClientConfig load(Path file) {
        try {
            return parse(YAML_MAPPER.readTree(Files.readString(file)), file.toString());
        } catch (IOException e) {
            throw SDKErrors.newError(e, SDKErrors.MSG_CONFIG_FILE_INVALID, file);
        }
    }

    /**
     * Load client config from a YAML stream.
     */
    public static ClientConfig load(InputStream inputStream) {
        try {
            return parse(YAML_MAPPER.readTree(inputStream), "(stream)");
        } catch (IOException e) {
            throw SDKErrors.newError(e, SDKErrors.MSG_CONFIG_FILE_INVALID, "(stream)");
        }
    }

    private static ClientConfig parse(JsonNode root, String source) {
        if (root == null || !root.isObject()) {
            throw SDKErrors.newError(SDKErrors.MSG_CONFIG_FILE_INVALID, source);
        }
        var section = root.path("workflow-engine");
        if (!section.isObject()) {
            throw SDKErrors.newError(SDKErrors.MSG_CONFIG_SECTION_MISSING, source);
        }

        var builder = ClientConfig.builder();

        var providerName = section.path("providerName").asText("");
        if (providerName.isEmpty()) {
            throw SDKErrors.newError(SDKErrors.MSG_PROVIDER_NAME_NOT_SET);
        }
        builder.providerName(providerName);

        if (section.has("providerMetadata")) {
            builder.providerMetadata(section.get("providerMetadata"));
        }
        if (section.has("maxRetries")) {
            builder.maxAttempts(section.get("maxRetries").asInt());
        }
        if (section.has("retryDelay")) {
            var retryNode = section.get("retryDelay");
            var retryValue = retryNode.isNumber() ? String.valueOf(retryNode.asInt()) : retryNode.asText();
            builder.reconnectDelay(parseTimeString(retryValue));
        }
        if (section.has("headers") && section.get("headers").isObject()) {
            var headers = new java.util.LinkedHashMap<String, String>();
            section.get("headers").fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) {
                    headers.put(entry.getKey(), entry.getValue().asText());
                }
            });
            builder.extraHeaders(headers);
        }

        var lifecycle = section.path("setupLifecycle").asText("");
        if (SetupLifecycle.VALUE_BOOT.equals(lifecycle)) {
            builder.setupLifecycle(SetupLifecycle.BOOT);
        } else if (SetupLifecycle.VALUE_DEFERRED.equals(lifecycle)) {
            builder.setupLifecycle(SetupLifecycle.DEFERRED);
        } else if (!lifecycle.isEmpty()) {
            log.warn("Unknown setupLifecycle '{}'; using default 'boot'", lifecycle);
        }

        var url = section.path("url").asText("");
        var authNode = section.path("auth");
        if (url.isEmpty() || !authNode.isObject()) {
            throw SDKErrors.newError(SDKErrors.MSG_CONFIG_URL_AUTH_MISSING, source);
        }
        builder.url(java.net.URI.create(httpUrlToWsUrl(url)));
        builder.auth(parseAuth(authNode));

        builder.serviceBindings(parseServiceBindings(root, section));
        builder.customConfig(resolveCustomConfig(root));

        return builder.build();
    }

    private static Map<String, ServiceBindingConfig> parseServiceBindings(JsonNode root, JsonNode section) {
        var bindingsSection = root.has("service-bindings")
                ? root.get("service-bindings")
                : section.get("service-bindings");
        return ServiceBindings.parseSection(bindingsSection);
    }

    private static AuthConfig parseAuth(JsonNode auth) {
        var type = auth.path("type").asText("token");
        return switch (type) {
            case "basic" -> new AuthConfig.BasicAuth(
                    auth.path("username").asText(""),
                    auth.path("password").asText(""));
            case "token" -> new AuthConfig.TokenAuth(
                    auth.path("token").asText(""),
                    auth.has("header") ? auth.get("header").asText() : null,
                    auth.has("scheme") ? auth.get("scheme").asText() : null);
            default -> throw SDKErrors.newError(SDKErrors.MSG_CONFIG_UNKNOWN_AUTH_TYPE, type);
        };
    }

    /**
     * Resolve provider-specific custom config from {@code CONFIG_FILE} (or the
     * default path), then the {@code config:} key on an already-parsed Kaleido
     * config root.
     */
    private static JsonNode resolveCustomConfig(JsonNode kaleidoRoot) {
        var providerConfigPath = System.getenv(CONFIG_FILE);
        if (providerConfigPath == null || providerConfigPath.isBlank()) {
            providerConfigPath = DEFAULT_PROVIDER_CONFIG_PATH;
        }
        try {
            var parsed = YAML_MAPPER.readTree(Files.readString(Path.of(providerConfigPath.trim())));
            if (parsed != null && !parsed.isMissingNode()) {
                return parsed;
            }
        } catch (IOException e) {
            // file absent or unreadable — fall back to 'config:' key in the Kaleido config
        }
        if (kaleidoRoot != null && kaleidoRoot.has("config")) {
            return kaleidoRoot.get("config");
        }
        return null;
    }

    /**
     * Resolve the Kaleido config file path: explicit argument, then
     * {@code KALEIDO_CONFIG_FILE}, then legacy {@code WFE_CONFIG_FILE}.
     * Returns null when none is set.
     */
    public static String resolveConfigPath(String explicitPath) {
        var path = explicitPath;
        if (path == null || path.isBlank()) {
            path = System.getenv(KALEIDO_CONFIG_FILE);
        }
        if (path == null || path.isBlank()) {
            path = System.getenv(WFE_CONFIG_FILE);
        }
        return path == null || path.isBlank() ? null : path.trim();
    }

    /**
     * Build a WebSocket URL from an HTTP(S) base URL: strips a trailing
     * {@code /rest}, converts the scheme, and appends {@code /ws}.
     */
    public static String httpUrlToWsUrl(String url) {
        var result = url;
        if (result.endsWith("/rest")) {
            result = result.substring(0, result.length() - 5);
        }
        result = result.replaceAll("/+$", "");
        if (result.startsWith("http://")) {
            result = "ws://" + result.substring(7);
        } else if (result.startsWith("https://")) {
            result = "wss://" + result.substring(8);
        }
        if (!result.endsWith("/ws")) {
            result += "/ws";
        }
        return result;
    }

    /**
     * Build a REST base URL from a WebSocket URL: strips a trailing {@code /ws},
     * converts the scheme, and appends {@code /rest}.
     */
    public static String wsUrlToRestUrl(String wsUrl) {
        var result = wsUrl;
        if (result.endsWith("/ws")) {
            result = result.substring(0, result.length() - 3);
        }
        if (!result.endsWith("/rest")) {
            result += "/rest";
        }
        if (result.startsWith("ws://")) {
            result = "http://" + result.substring(5);
        } else if (result.startsWith("wss://")) {
            result = "https://" + result.substring(6);
        }
        return result;
    }

    /**
     * Parse a retry-delay/time string. A plain integer is seconds; otherwise a
     * time string with unit ({@code 100ms}, {@code 2s}, {@code 1m}, {@code 1h}).
     * Invalid or empty values return the 2-second default.
     */
    public static Duration parseTimeString(String value) {
        if (value == null || value.isEmpty()) {
            return Duration.ofSeconds(2);
        }
        if (value.matches("^\\d+$")) {
            return Duration.ofSeconds(Long.parseLong(value));
        }
        var matcher = TIME_PATTERN.matcher(value.trim());
        if (matcher.matches()) {
            var amount = Long.parseLong(matcher.group(1));
            return switch (matcher.group(2)) {
                case "ms" -> Duration.ofMillis(amount);
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                default -> Duration.ofSeconds(2);
            };
        }
        return Duration.ofSeconds(2);
    }
}
