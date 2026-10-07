// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ServiceBindingsTest {

    @TempDir
    Path dir;

    private Path config(String yaml) throws IOException {
        return Files.writeString(dir.resolve("config.yaml"), yaml);
    }

    private static final String BINDINGS = """
            service-bindings:
              asset-manager:
                bindingType: non-hosted
                serviceType: AssetManagerService
                url: http://localhost:8000
                maxRetries: 2
                timeout: 5000
                auth:
                  type: token
                  token: secret
                  scheme: Bearer
              connector:
                bindingType: hosted
                serviceType: EVMConnectorService
                id: svc-connector-001
            """;

    @Test
    void bindingsUnderWorkflowEngineAreFoundWhenThereAreNoneAtTheTop() throws IOException {
        var file = config("workflow-engine:\n" + BINDINGS.indent(2));

        assertEquals(List.of("asset-manager", "connector"), List.copyOf(ServiceBindings.load(file).keySet()));
    }

    @Test
    void topLevelBindingsWin() throws IOException {
        var file = config(BINDINGS + """
                workflow-engine:
                  service-bindings:
                    other:
                      url: http://other
                      auth: {type: basic}
                """);

        assertEquals(List.of("asset-manager", "connector"), List.copyOf(ServiceBindings.load(file).keySet()));
    }

    @Test
    void aFileWithoutBindingsHasNone() throws IOException {
        assertTrue(ServiceBindings.load(config("workflow-engine:\n  providerName: p\n")).isEmpty());
        assertTrue(ServiceBindings.load(config("")).isEmpty());
        assertTrue(ServiceBindings.load(config("---\n# nothing yet\n")).isEmpty());
        assertTrue(ServiceBindings.load(config("~\n")).isEmpty());
    }

    @Test
    void aTopLevelKeyWithNoValueFallsBackToTheNestedBindings() throws IOException {
        var file = config("service-bindings:\nworkflow-engine:\n" + BINDINGS.indent(2));

        assertEquals(List.of("asset-manager", "connector"), List.copyOf(ServiceBindings.load(file).keySet()));
    }

    @Test
    void anEmptyTopLevelMappingStillWins() throws IOException {
        var file = config("service-bindings: {}\nworkflow-engine:\n" + BINDINGS.indent(2));

        assertTrue(ServiceBindings.load(file).isEmpty());
    }

    @Test
    void anInvalidOrMissingFileFails() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> ServiceBindings.load(config("- a list\n")));
        assertThrows(IllegalArgumentException.class, () -> ServiceBindings.load(config("a: [unclosed\n")));
        assertThrows(UncheckedIOException.class, () -> ServiceBindings.load(dir.resolve("missing.yaml")));
    }

    // ── Config file from the environment ────────────────────────────────────

    @Test
    void theConfigFileNamedByTheEnvIsTrimmed() {
        assertEquals(Path.of("/etc/kaleido/config.yaml"), ServiceBindings.configFile("  /etc/kaleido/config.yaml \n"));
    }

    @Test
    void anUnsetOrBlankEnvFails() {
        for (var value : new String[] {null, "", "   "}) {
            var error = assertThrows(IllegalStateException.class, () -> ServiceBindings.configFile(value));
            assertTrue(error.getMessage().contains("KALEIDO_CONFIG_FILE is not set"), error.getMessage());
        }
    }

    @Test
    void loadingFromTheEnvFailsWhenItIsNotSet() {
        assumeTrue(System.getenv(ServiceBindings.KALEIDO_CONFIG_FILE) == null, "KALEIDO_CONFIG_FILE is set");

        assertThrows(IllegalStateException.class, ServiceBindings::configFile);
        assertThrows(IllegalStateException.class, ServiceBindings::load);
    }

    // ── Parsing a section ───────────────────────────────────────────────────

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static Map<String, ServiceBindingConfig> parse(String yaml) throws IOException {
        return ServiceBindings.parseSection(YAML.readTree(yaml));
    }

    @Test
    void aMissingOrNonMappingSectionHasNoBindings() throws IOException {
        assertTrue(ServiceBindings.parseSection(null).isEmpty());
        assertTrue(parse("[a, b]").isEmpty());
        assertTrue(parse("just text").isEmpty());
        assertTrue(ServiceBindings.fromDocument(null).isEmpty());
        assertTrue(ServiceBindings.fromDocument(YAML.readTree("[1]")).isEmpty());
        assertTrue(ServiceBindings.fromDocument(YAML.readTree("service-bindings: [1]")).isEmpty());
        assertTrue(ServiceBindings.fromDocument(YAML.readTree("workflow-engine: text")).isEmpty());
    }

    @Test
    void invalidAndIncompleteBindingsAreSkipped() throws IOException {
        var bindings = parse("""
                not-a-mapping: text
                empty:
                no-id:
                  bindingType: hosted
                blank-id:
                  bindingType: hosted
                  id: "  "
                no-url:
                  auth: {type: basic}
                no-auth:
                  url: http://x
                auth-not-a-mapping:
                  url: http://x
                  auth: token
                good:
                  url: http://x
                  auth: {type: basic}
                """);

        assertEquals(List.of("good"), List.copyOf(bindings.keySet()));
    }

    @Test
    void theServiceTypeIsReadFromServiceTypeThenTypeThenTheName() throws IOException {
        var bindings = parse("""
                both:
                  serviceType: FromServiceType
                  type: FromType
                  bindingType: hosted
                  id: a
                legacy:
                  type: FromType
                  bindingType: hosted
                  id: b
                by-name:
                  bindingType: hosted
                  id: c
                blank:
                  serviceType: " "
                  bindingType: hosted
                  id: d
                """);

        assertEquals("FromServiceType", bindings.get("both").type());
        assertEquals("FromType", bindings.get("legacy").type());
        assertEquals("by-name", bindings.get("by-name").type());
        assertEquals("blank", bindings.get("blank").type());
    }

    @Test
    void aBindingWithoutABindingTypeIsNonHosted() throws IOException {
        var bindings = parse("""
                default:
                  url: " http://x "
                  auth: {type: basic, username: u}
                unknown:
                  bindingType: something-else
                  url: http://y
                  auth: {type: basic}
                """);

        var binding = assertInstanceOf(ServiceBindingConfig.NonHosted.class, bindings.get("default"));
        assertEquals("http://x", binding.url());
        assertInstanceOf(ServiceBindingConfig.NonHosted.class, bindings.get("unknown"));
    }

    @Test
    void aHostedBindingKeepsItsIdRetriesAndTimeout() throws IOException {
        var bindings = parse("""
                am:
                  bindingType: hosted
                  serviceType: AssetManagerService
                  id: s:am1
                  maxRetries: 3
                  timeout: 1500
                """);

        assertEquals(new ServiceBindingConfig.Hosted("AssetManagerService", "s:am1", 3, 1500), bindings.get("am"));
    }

    @Test
    void authDefaultsToBasicAndKeepsOnlyItsOwnFields() throws IOException {
        var bindings = parse("""
                basic:
                  url: http://x
                  auth: {username: u, password: p, token: ignored}
                basic-empty:
                  url: http://x
                  auth: {type: basic, username: "", password: " "}
                token:
                  url: http://x
                  auth: {type: token, token: t, header: X-Api-Key, scheme: Bearer, username: ignored}
                token-bare:
                  url: http://x
                  auth: {type: token, token: t, header: "", scheme: ""}
                other:
                  url: http://x
                  auth: {type: oauth, username: u}
                """);

        assertEquals(ServiceBindingAuth.basic("u", "p"), auth(bindings, "basic"));
        assertEquals(ServiceBindingAuth.basic(null, null), auth(bindings, "basic-empty"));
        assertEquals(ServiceBindingAuth.token("t", "X-Api-Key", "Bearer"), auth(bindings, "token"));
        assertEquals(ServiceBindingAuth.token("t", null, null), auth(bindings, "token-bare"));
        assertEquals(new ServiceBindingAuth("oauth", "u", null, null, null, null), auth(bindings, "other"));
    }

    private static ServiceBindingAuth auth(Map<String, ServiceBindingConfig> bindings, String name) {
        return assertInstanceOf(ServiceBindingConfig.NonHosted.class, bindings.get(name)).auth();
    }

    @Test
    void retriesAndTimeoutAcceptNumbersOrNumericText() throws IOException {
        var bindings = parse("""
                numbers: {bindingType: hosted, id: a, maxRetries: 2, timeout: 100}
                text: {bindingType: hosted, id: b, maxRetries: " 4 ", timeout: "200"}
                invalid: {bindingType: hosted, id: c, maxRetries: lots, timeout: true}
                missing: {bindingType: hosted, id: d}
                """);

        assertRetriesAndTimeout(bindings.get("numbers"), 2, 100);
        assertRetriesAndTimeout(bindings.get("text"), 4, 200);
        assertRetriesAndTimeout(bindings.get("invalid"), null, null);
        assertRetriesAndTimeout(bindings.get("missing"), null, null);
    }

    private static void assertRetriesAndTimeout(ServiceBindingConfig binding, Integer maxRetries, Integer timeout) {
        assertEquals(maxRetries, binding.maxRetries());
        assertEquals(timeout, binding.timeout());
    }

    @Test
    void nonTextValuesAreIgnored() throws IOException {
        var bindings = parse("""
                numeric-id: {bindingType: hosted, id: 42}
                numeric-url:
                  url: 8080
                  auth: {type: basic}
                """);

        assertTrue(bindings.isEmpty());
    }

    @Test
    void bindingsKeepTheirConfigOrder() throws IOException {
        var bindings = parse("""
                c: {bindingType: hosted, id: c}
                a: {bindingType: hosted, id: a}
                b: {bindingType: hosted, id: b}
                """);

        assertEquals(List.of("c", "a", "b"), List.copyOf(bindings.keySet()));
    }
}
