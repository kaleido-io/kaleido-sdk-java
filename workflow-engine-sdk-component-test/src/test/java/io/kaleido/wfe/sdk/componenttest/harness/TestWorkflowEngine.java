// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.componenttest.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Dual-mode test harness for component tests. Starts a real WFE instance
 * backed by Postgres and exposes REST/WS URLs for tests.
 *
 * <p>Supports two modes:</p>
 * <ul>
 *   <li><b>source</b> -- Builds WFE from sibling firefly-enterprise repo,
 *       starts Postgres via Docker Compose, runs WFE as a subprocess.</li>
 *   <li><b>docker</b> -- Runs both Postgres and WFE as containers via
 *       Docker Compose. No Go toolchain or sibling repo needed.</li>
 * </ul>
 *
 * <p>Mode is selected via {@code WFE_TEST_MODE} env var ({@code source} or
 * {@code docker}), or auto-detected: source if sibling repo + Go are available,
 * otherwise Docker.</p>
 */
public class TestWorkflowEngine implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration HEALTH_POLL_INTERVAL = Duration.ofMillis(500);
    private static final Duration HEALTH_TIMEOUT_SOURCE = Duration.ofSeconds(30);
    private static final Duration HEALTH_TIMEOUT_DOCKER = Duration.ofSeconds(60);
    private static final Duration TRANSACTION_POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration TRANSACTION_TIMEOUT = Duration.ofSeconds(30);

    private static final Path REPO_ROOT = findRepoRoot();

    enum Mode { SOURCE, DOCKER }

    private final Mode mode;
    private final String baseUrl;
    private final String wsUrl;
    private final Process wfeProcess;       // source mode only
    private final Path configPath;          // source mode only
    private final HttpServer mockAuthServer; // mock access manager
    private final HttpClient httpClient;

    private TestWorkflowEngine(Mode mode, String baseUrl, String wsUrl,
                               Process wfeProcess, Path configPath,
                               HttpServer mockAuthServer) {
        this.mode = mode;
        this.baseUrl = baseUrl;
        this.wsUrl = wsUrl;
        this.wfeProcess = wfeProcess;
        this.configPath = configPath;
        this.mockAuthServer = mockAuthServer;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String wsUrl() {
        return wsUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    // --- REST API helpers ---

    public JsonNode deployWorkflow(String name, String yamlContent) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/workflows/" + name))
                .header("Content-Type", "application/x-yaml")
                .header("X-Kld-Authz", "component-test")
                .PUT(HttpRequest.BodyPublishers.ofString(yamlContent))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("Failed to deploy workflow '" + name + "': "
                    + response.statusCode() + " " + response.body());
        }
        return MAPPER.readTree(response.body());
    }

    public JsonNode submitTransaction(String workflowId, String operation, JsonNode input) throws Exception {
        var body = MAPPER.createObjectNode();
        body.put("workflow", workflowId);
        body.put("operation", operation);
        body.set("input", input);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/transactions"))
                .header("Content-Type", "application/json")
                .header("X-Kld-Authz", "component-test")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("Failed to submit transaction: "
                    + response.statusCode() + " " + response.body());
        }
        return MAPPER.readTree(response.body());
    }

    public JsonNode waitForTransaction(String transactionId) throws Exception {
        long deadline = System.currentTimeMillis() + TRANSACTION_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            var txn = getTransactionWithState(transactionId);
            var status = txn.path("status").asText("");
            if ("success".equals(status) || "failure".equals(status)) {
                return txn;
            }
            Thread.sleep(TRANSACTION_POLL_INTERVAL.toMillis());
        }
        throw new RuntimeException("Transaction " + transactionId
                + " did not reach terminal state within " + TRANSACTION_TIMEOUT);
    }

    public List<JsonNode> waitForTransactions(List<String> transactionIds) throws Exception {
        return transactionIds.stream()
                .map(id -> {
                    try {
                        return waitForTransaction(id);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    public JsonNode getTransactionWithState(String transactionId) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/transactions/" + transactionId + "?state=true"))
                .header("X-Kld-Authz", "component-test")
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("Failed to get transaction: "
                    + response.statusCode() + " " + response.body());
        }
        return MAPPER.readTree(response.body());
    }

    // --- Lifecycle ---

    @Override
    public void close() {
        switch (mode) {
            case SOURCE -> closeSource();
            case DOCKER -> closeDocker();
        }
    }

    private void closeSource() {
        if (wfeProcess != null && wfeProcess.isAlive()) {
            wfeProcess.destroy();
            try {
                if (!wfeProcess.waitFor(10, TimeUnit.SECONDS)) {
                    wfeProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                wfeProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        if (mockAuthServer != null) {
            mockAuthServer.stop(0);
        }
        if (configPath != null) {
            try { Files.deleteIfExists(configPath); } catch (IOException ignored) {}
        }
        composeDown("component-test-source.yml");
    }

    private void closeDocker() {
        if (mockAuthServer != null) {
            mockAuthServer.stop(0);
        }
        composeDown("component-test-docker.yml");
    }

    // --- Mode detection ---

    public static Mode selectMode() {
        String explicit = System.getenv("WFE_TEST_MODE");
        if (explicit == null || explicit.isEmpty()) {
            explicit = System.getProperty("wfe.test.mode", "");
        }
        if ("source".equalsIgnoreCase(explicit)) return Mode.SOURCE;
        if ("docker".equalsIgnoreCase(explicit)) return Mode.DOCKER;

        // Auto-detect: source if sibling repo + Go exist
        if (isSourceModeAvailable()) return Mode.SOURCE;
        return Mode.DOCKER;
    }

    /**
     * Returns true if component tests can run in at least one mode.
     */
    public static boolean isAvailable() {
        if (!hasCommand("docker")) return false;
        // Docker mode always works if docker is present
        return true;
    }

    // --- Start (unified entry point) ---

    public static TestWorkflowEngine start() throws Exception {
        Mode mode = selectMode();
        return switch (mode) {
            case SOURCE -> startSource();
            case DOCKER -> startDocker();
        };
    }

    // --- Source mode ---

    private static TestWorkflowEngine startSource() throws Exception {
        Path wfeDir = locateWfeSourceDir();
        Path wfeBinary = wfeDir.resolve("workflow-engine");

        // Build WFE binary
        int buildExit = new ProcessBuilder("go", "build", "-o", "workflow-engine", "./main.go")
                .directory(wfeDir.toFile())
                .inheritIO()
                .start()
                .waitFor();
        if (buildExit != 0) {
            throw new RuntimeException("go build failed with exit code " + buildExit);
        }

        // Start mock access manager
        HttpServer authServer = startMockAuthServer();
        int authPort = authServer.getAddress().getPort();

        // Start Postgres via Compose
        composeUp("component-test-source.yml");
        Thread.sleep(5000); // wait for Postgres to accept connections

        // Drop/recreate test database via TCP URL (not Unix socket)
        String pgUrl = "postgres://postgres:my-secret@localhost:5432/postgres?sslmode=disable";
        execCompose("component-test-source.yml", "db",
                "psql", pgUrl, "-c", "DROP DATABASE IF EXISTS sdk_test WITH (FORCE)");
        execCompose("component-test-source.yml", "db",
                "psql", pgUrl, "-c", "CREATE DATABASE sdk_test");

        // Generate temp config matching what WFE expects
        int apiPort = findFreePort();
        int monitoringPort = findFreePort();
        String migrationsDir = wfeDir.resolve("db/migrations/postgres").toString();
        String configYaml = """
                platform:
                  environmentId: s:componenttest
                  serviceId: s:sdktest00000
                database:
                  postgres:
                    url: postgres://postgres:my-secret@localhost:5432/sdk_test?sslmode=disable
                    migrations:
                      directory: %s
                      auto: true
                api:
                  port: %d
                accessmanager:
                  url: http://127.0.0.1:%d
                monitoring:
                  port: %d
                log:
                  level: trace
                engine:
                  errorRetry:
                    initialDelay: 10ms
                    maxDelay: 250ms
                    factor: 2.0
                """.formatted(migrationsDir, apiPort, authPort, monitoringPort);

        Path configPath = Files.createTempFile("wfe-test-config-", ".yaml");
        Files.writeString(configPath, configYaml, StandardCharsets.UTF_8);

        // Start WFE subprocess
        Process process = new ProcessBuilder(
                wfeBinary.toString(), "--enable-reset-api", "-f", configPath.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();

        // Health check
        String baseUrl = "http://127.0.0.1:" + apiPort;
        pollUntilHealthy(baseUrl, HEALTH_TIMEOUT_SOURCE);

        String wsUrl = "ws://127.0.0.1:" + apiPort + "/ws";
        return new TestWorkflowEngine(Mode.SOURCE, baseUrl, wsUrl, process, configPath, authServer);
    }

    // --- Docker mode ---

    private static TestWorkflowEngine startDocker() throws Exception {
        // Start mock access manager on the host (WFE container reaches it via host.docker.internal)
        HttpServer authServer = startMockAuthServer();
        int authPort = authServer.getAddress().getPort();

        composeUpWithEnv("component-test-docker.yml",
                "MOCK_AUTH_URL=http://host.docker.internal:" + authPort);

        String baseUrl = "http://localhost:5000";
        pollUntilHealthy(baseUrl, HEALTH_TIMEOUT_DOCKER);

        return new TestWorkflowEngine(Mode.DOCKER, baseUrl, "ws://localhost:5000/ws", null, null, authServer);
    }

    // --- Utilities ---

    private static void pollUntilHealthy(String baseUrl, Duration timeout) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                var req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/v1/status"))
                        .GET()
                        .timeout(Duration.ofSeconds(2))
                        .build();
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) return;
            } catch (Exception ignored) {}
            Thread.sleep(HEALTH_POLL_INTERVAL.toMillis());
        }
        throw new RuntimeException("WFE failed to become healthy within " + timeout + " at " + baseUrl);
    }

    private static boolean isSourceModeAvailable() {
        try {
            locateWfeSourceDir();
            return hasCommand("go");
        } catch (Exception e) {
            return false;
        }
    }

    private static Path locateWfeSourceDir() {
        String envDir = System.getenv("FIREFLY_ENTERPRISE_DIR");
        if (envDir == null || envDir.isEmpty()) {
            envDir = System.getProperty("firefly.enterprise.dir", "");
        }
        Path candidate;
        if (!envDir.isEmpty()) {
            candidate = Path.of(envDir, "workflow-engine");
        } else {
            // Sibling auto-detection: ../firefly-enterprise relative to repo root
            candidate = REPO_ROOT.resolve("../firefly-enterprise/workflow-engine").normalize();
        }
        if (Files.isRegularFile(candidate.resolve("main.go"))) {
            return candidate;
        }
        throw new IllegalStateException("Cannot locate firefly-enterprise/workflow-engine. "
                + "Set FIREFLY_ENTERPRISE_DIR or ensure ../firefly-enterprise/ exists as a sibling.");
    }

    private static boolean hasCommand(String command) {
        try {
            var process = new ProcessBuilder("which", command)
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void composeUp(String composeFile) throws Exception {
        int exit = new ProcessBuilder("docker", "compose", "-f",
                REPO_ROOT.resolve(composeFile).toString(), "up", "-d")
                .inheritIO()
                .start()
                .waitFor();
        if (exit != 0) {
            throw new RuntimeException("docker compose up failed for " + composeFile);
        }
    }

    private static void composeUpWithEnv(String composeFile, String... envVars) throws Exception {
        var pb = new ProcessBuilder("docker", "compose", "-f",
                REPO_ROOT.resolve(composeFile).toString(), "up", "-d");
        pb.inheritIO();
        var env = pb.environment();
        for (String kv : envVars) {
            int eq = kv.indexOf('=');
            env.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        int exit = pb.start().waitFor();
        if (exit != 0) {
            throw new RuntimeException("docker compose up failed for " + composeFile);
        }
    }

    /**
     * Starts a minimal HTTP server that mocks the Kaleido access manager endpoints.
     * The WFE validates auth tokens by calling these endpoints.
     */
    private static HttpServer startMockAuthServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        server.createContext("/api/v1/auth/generate-token-set", exchange -> {
            byte[] response = """
                    {"refreshToken":"test-refresh-token","identity":"test-identity"}""".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.createContext("/api/v1/auth/refresh-auth-token", exchange -> {
            byte[] response = """
                    {"authToken":"test-refresh-token"}""".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();
        return server;
    }

    private static void composeDown(String composeFile) {
        try {
            new ProcessBuilder("docker", "compose", "-f",
                    REPO_ROOT.resolve(composeFile).toString(), "down", "-v")
                    .inheritIO()
                    .start()
                    .waitFor(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }

    private static void execCompose(String composeFile, String service, String... command) throws Exception {
        var args = new java.util.ArrayList<String>();
        args.add("docker");
        args.add("compose");
        args.add("-f");
        args.add(REPO_ROOT.resolve(composeFile).toString());
        args.add("exec");
        args.add("-T");
        args.add(service);
        args.addAll(java.util.Arrays.asList(command));

        int exit = new ProcessBuilder(args).inheritIO().start().waitFor();
        if (exit != 0) {
            throw new RuntimeException("exec in " + service + " failed: " + String.join(" ", command));
        }
    }

    private static int findFreePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Path findRepoRoot() {
        // Walk up from the class location or use working directory
        Path cwd = Path.of(System.getProperty("user.dir"));
        // If we're in a subproject, go up to the root (has settings.gradle.kts)
        Path candidate = cwd;
        for (int i = 0; i < 5; i++) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) {
                return candidate;
            }
            candidate = candidate.getParent();
            if (candidate == null) break;
        }
        return cwd;
    }
}
