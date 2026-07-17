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

Point these tests at a workflow engine that is already running and reachable over
HTTP. They do not start or manage that process for you.

Local development defaults assume `http://localhost:5503` with token auth
(`dev-token-123` in the `X-Kld-Authz` header). Override as needed (see below).

## Running

```bash
./gradlew componentTest
```

The `componentTest` task is not part of `check`, so `./gradlew build` stays green
without a running engine.

## Configuration

To override the defaults, either:

- set env vars: `FLOW_ENGINE_URL`, `WORKFLOW_ENGINE_AUTH_TOKEN`,
  `WORKFLOW_ENGINE_AUTH_HEADER`, `WORKFLOW_ENGINE_AUTH_SCHEME`; or
- copy `src/componentTest/resources/test-config.template.yaml` to
  `test-config.yaml` next to it and edit (the file is picked up from the test
  classpath).

## Cleanup

Tests delete the workflows and streams they create. If a run is interrupted,
resources may remain in the workflow engine and need manual cleanup.
