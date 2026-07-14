# Component Tests

Conformance tests for the Workflow Engine Java SDK that run against a live workflow
engine instance. They use the same workflow YAML fixtures as the TypeScript SDK's
component test suite (`tests/componenttest/workflows/` in `kaleido-sdk-typescript`),
so both SDKs are held to the same end-to-end behavior:

- **`ThreeRingedCircusTest`** — StageDirector actions, JSON Patch state updates,
  custom stage transitions, config profiles, and `TRANSIENT_ERROR` retries.
- **`SnapTest`** — triggers, `WAITING` results, event sources with checkpointing,
  and topic-based correlation across two provider connections.

## Prerequisites

The workflow engine must be running before the tests execute — they do not start it.
The simplest way is the connector-toolkit compose stack in `firefly-enterprise`:

```bash
cd firefly-enterprise/common/connector-toolkit
docker compose -f dev.compose.yaml up -d --build
```

## Running

```bash
./gradlew componentTest
```

The `componentTest` task is not part of `check`, so `./gradlew build` stays green
without a running engine.

## Configuration

Defaults target the compose stack (`http://localhost:5503`, token `dev-token-123`
in the `X-Kld-Authz` header). To override, either:

- set env vars: `FLOW_ENGINE_URL`, `WORKFLOW_ENGINE_AUTH_TOKEN`,
  `WORKFLOW_ENGINE_AUTH_HEADER`, `WORKFLOW_ENGINE_AUTH_SCHEME`; or
- copy `src/componentTest/resources/test-config.template.yaml` to
  `test-config.yaml` next to it and edit (the file is picked up from the test
  classpath).

## Cleanup

Tests delete the workflows and streams they create. If a run is interrupted,
resources may remain in the workflow engine and need manual cleanup.
