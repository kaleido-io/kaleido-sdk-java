# Workflow Engine getting-started sample

A minimal Spring Boot provider that registers two handlers with the workflow engine:

- `hello` — a transaction handler that logs each transaction and completes it
  into the `complete` stage.
- `echo` — an event processor that logs each event batch it receives.

This sample is source-only: it is not wired into the repository's Gradle build.
Copy the classes into a Spring Boot project that depends on
`io.kaleido:workflow-engine-sdk` (plus `spring-boot-starter`), or use them as a
reference for wiring the SDK into your own application.

## Configuration

The app resolves its config in this order:

1. `KALEIDO_CONFIG_FILE` env var (or the legacy `WFE_CONFIG_FILE`)
2. `kaleido-config.yaml` on the classpath (see
   [`src/main/resources/kaleido-config.yaml`](src/main/resources/kaleido-config.yaml))
3. `config/kaleido-config.yaml` on the filesystem

Edit the config to point at your workflow engine endpoint and credentials. The
`providerName` must match the provider referenced by your workflow's handler
bindings.

Once connected, the provider appears in the workflow engine's provider list and
`hello`/`echo` can be referenced from workflow definitions and streams.
