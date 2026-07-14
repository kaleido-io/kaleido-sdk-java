# Kaleido Workflow Engine Java SDK

A Java SDK for building handlers that integrate with the Kaleido workflow engine.

Using the workflow engine SDK you can build applications called **providers** that
interact with the workflow engine. The provider types supported are:

- **Transaction handlers** — execute workflow stage actions when the engine sends
  transaction batches (business logic, external API calls, stage transitions).
- **Event sources** — poll or subscribe to external systems and emit events (with
  checkpoints) into the workflow engine.
- **Event processors** — receive event batches from the engine and run your
  processing logic against them, with optional setup hooks, typed config, and
  service-binding helpers.

More information on the workflow engine programming model is available from the
[Kaleido platform docsite](https://docs.kaleido.io/platform/web3-middleware/workflowengine/).
The TypeScript equivalent of this SDK is
[@kaleido-io/workflow-engine-sdk](https://github.com/kaleido-io/kaleido-sdk-typescript) —
the two share the same wire protocol, configuration files, and programming model.

## Installation

See the [repository README](../README.md) for GitHub Packages setup. Then:

```kotlin
dependencies {
    implementation("io.kaleido:workflow-engine-sdk:<version>")
}
```

Requires JDK 21 or later. The SDK logs through SLF4J — add the binding of your
choice (`slf4j-simple`, Logback, etc.).

## Running hosted or non-hosted

Providers can run in one of two modes:

- **Hosted** — the provider is built as a container image and runs as a Kaleido
  managed service. The platform generates the workflow engine connection and
  hosted service bindings, and the provider's WebSocket is routed through the
  provider proxy. This is the intended mode for production.
- **Non-hosted** — the provider runs on your workstation (as a Java application
  or a container) and connects outbound to the workflow engine, with connection
  details supplied in config. Intended for fast iteration during development.

In both cases your code is the same; only the configuration differs.

## Configuration model

Most provider flows use two config files:

- `config.yaml` — platform connectivity and service bindings
- `provider-config.yaml` — your app-specific config

### Environment variables

- `KALEIDO_CONFIG_FILE` — path to `config.yaml` (`WFE_CONFIG_FILE` is a
  deprecated alias)
- `CONFIG_FILE` — path to `provider-config.yaml` (defaults to
  `./config/provider-config.yaml`)

`WorkflowEngineClient.fromConfigFile()` reads both. When running hosted, the
platform writes these files and sets the variables for you.

### Platform config (`config.yaml`)

The workflow engine connection is defined by the top-level `workflow-engine`
key; named service bindings live under `service-bindings`:

```yaml
workflow-engine:
  providerName: my-provider
  url: http://localhost:5503
  auth:
    type: token           # or "basic" with username/password
    token: dev-token-123
    header: X-Kld-Authz   # optional, defaults to Authorization
    scheme: ""            # optional, e.g. "Bearer" for "Bearer <token>"
  retryDelay: 2s          # time string: ms, s, m, h (plain number = seconds)
  # maxRetries: omit for infinite reconnection (recommended)
  # setupLifecycle: boot  # or "deferred" — see setup hooks below

service-bindings:
  asset-manager:
    type: asset-manager
    bindingType: non-hosted
    url: https://am.example.com/api/v1
    auth:
      type: token
      token: ${AM_TOKEN}
      scheme: Bearer

  # Hosted binding example (resolved via the ws-proxy transport)
  evm-connector:
    type: connector
    bindingType: hosted
    id: svc-connector-001
```

A service binding maps a name to a service's connection information. Swapping a
binding between `non-hosted` (you supply URL and auth) and `hosted` (the
platform resolves the instance through the provider proxy) requires no code
change, so the same provider runs locally and hosted.

### Provider config (`provider-config.yaml`)

Your own application settings — batch sizes, allowlists, polling windows — not
platform connection details. Handlers access it as a Jackson tree or a typed
object:

```yaml
batchSize: 50
allowlist:
  - '0x0000000000000000000000000000000000000001'
```

```java
public record MyConfig(int batchSize, List<String> allowlist) {}

// inside a setup hook or event processor batch:
MyConfig config = ctx.config(MyConfig.class);
```

## Core concepts

### WorkflowEngineClient

The main entry point. It manages handler registration, the connection
lifecycle, automatic reconnection and re-registration, and message routing
between the engine and your handlers.

```java
// From config files (recommended — uses KALEIDO_CONFIG_FILE / CONFIG_FILE)
var client = WorkflowEngineClient.fromConfigFile();

// From an explicit config file
var client = WorkflowEngineClient.fromConfigFile(Path.of("/path/to/config.yaml"));

// Programmatic config
var client = new WorkflowEngineClient(ClientConfig.builder()
        .url(URI.create("ws://localhost:5503/ws"))
        .providerName("my-service")
        .auth(new AuthConfig.TokenAuth("your-token", "X-Kld-Authz", null))
        .build());
```

Register handlers with the fluent builder methods, then `start()`:

```java
client.transactionHandler("my-handler", myHandler)
      .eventSource(myEventSource)
      .start();
```

`start()` connects, registers everything, and (by default) runs setup hooks.
The low-level `registerTransactionHandler` / `registerEventSource` /
`registerEventProcessor` + `connect()` methods remain available when you don't
need setup hooks.

## Transaction handlers

Directed transaction handlers route each transaction to an **action** named in
its input, using the StageDirector pattern: the input carries `action`,
`outputPath`, `nextStage`, and `failureStage` fields that control where output
is written and which stage the transaction moves to.

```java
public record ProcessInput(StageDirector stageDirector, String userId, double amount)
        implements WithStageDirector {}

Map<String, ActionConfig<ProcessInput>> actionMap = Map.of(
    "validatePayment", ActionConfig.parallel((transaction, input) -> {
        if (input.amount() <= 0) {
            return ActionResult.hardFailure(new IllegalArgumentException("Invalid amount"));
        }
        return ActionResult.complete()
                .withOutput(Map.of("validated", true))
                .withExtraUpdates(List.of(PatchOp.add("/validation", Map.of("valid", true))));
    }),
    "processPayment", ActionConfig.parallel((transaction, input) -> {
        var receipt = processPayment(input.userId(), input.amount());
        return ActionResult.complete()
                .withOutput(receipt)
                .withTriggers(List.of(new Trigger("payment.completed", null)));
    }));

var handler = TransactionHandlerFactory.createTransactionHandler(
        "payment-handler", ProcessInput.class, actionMap);

WorkflowEngineClient.fromConfigFile()
        .transactionHandler("payment-handler", handler)
        .start();
```

Notes:

- The input record needs a `StageDirector stageDirector` component. When the
  engine sends a plain JSON input without one, the SDK synthesizes it from the
  flat `action`/`outputPath`/`nextStage`/`failureStage` fields.
- `ActionConfig.parallel(fn)` runs each transaction concurrently on a virtual
  thread; `ActionConfig.batch(fn)` hands the whole action group to one call.
- Attach an optional setup hook by registering with
  `TransactionHandlerRegistration.of(handler, setupHook)`.
- For full control, implement the `TransactionHandler` interface directly: it
  receives a `RequestContext`, the mutable result to populate, and the batch.

### EngineAPI

Handlers can call back into the workflow engine to submit new transactions.
The `EngineAPI` is passed to each handler's `init` hook, and
`submitAsyncTransactions` blocks until the engine responds (handlers run on
virtual threads). It needs the dispatch's `RequestContext`, so use it from a
direct `TransactionHandler` implementation:

```java
public class SpawningHandler implements TransactionHandler {
    private EngineAPI engineAPI;

    @Override
    public String name() { return "spawning-handler"; }

    @Override
    public void init(EngineAPI engineAPI) { this.engineAPI = engineAPI; }

    @Override
    public void transactionHandlerBatch(RequestContext reqContext,
                                        WSHandleTransactionsResult result,
                                        WSHandleTransactions batch) throws Exception {
        for (var transaction : batch.transactions()) {
            List<IdempotentSubmitResult> submissions = engineAPI.submitAsyncTransactions(
                    reqContext, transaction.authRef(),
                    List.of(new AsyncTransactionInput(null, null,
                            JSON.MAPPER.valueToTree("flw:abc123"), "process",
                            JSON.MAPPER.valueToTree(Map.of("data", "value")), null)));
            result.getResults().add(WSEvaluateReplyResult.stage("submitted"));
        }
    }
}
```

## Event sources

Build an event source from a poll function with `EventSourceFactory`, then
register it on the client. Each poll receives the stream config and the last
checkpoint (null on first poll), and returns the events read plus the new
checkpoint:

```java
var mySource = EventSourceFactory.createEventSource("my-event-source",
        (conf, checkpointIn, authRef) -> {
            long lastId = checkpointIn != null ? checkpointIn.path("lastId").asLong() : 0;
            return EventSourcePollOutput.of(
                    Map.of("lastId", lastId + 1),
                    List.of(EventSourceEvent.of("evt-" + (lastId + 1), "my-topic",
                            Map.of("value", lastId + 1))));
        });

WorkflowEngineClient.fromConfigFile()
        .eventSource(mySource)
        .start();
```

Optional hooks on the builder: `withConfigParser` (typed stream config, parsed
once per stream), `withInitialCheckpoint` (checkpoint for new streams),
`withDeleteFn` (cleanup on stream removal), `withInitFn` / `withCloseFn`.

A poll failure is reported to the engine as an error on that poll result — the
checkpoint does not advance and the dispatch loop keeps running.

## Event processors

Register with `.eventProcessor()` and an `EventProcessorDef`. The batch
function receives an `EventProcessorContext` with:

- `ctx.config()` / `ctx.config(MyConfig.class)` — your `provider-config.yaml`
- `ctx.getServiceClientOptions(bindingName)` — resolve a service binding
  (hosted or non-hosted)
- `ctx.signal()` — per-request cancellation signal that respects the engine's
  request deadline
- `ctx.requestId()` — per-batch request ID for correlation logging

```java
WorkflowEngineClient.fromConfigFile()
        .eventProcessor("my-processor", EventProcessorDef.of(
                (ctx, events) -> {
                    var config = ctx.config(MyConfig.class);
                    for (var event : events) {
                        persist(event, config.batchSize());
                    }
                },
                ctx -> {
                    // optional setup hook: ensure streams, bootstrap resources...
                }))
        .start();
```

Throw from the batch function to mark the batch as failed; the SDK surfaces the
error to the engine.

### Setup hooks and `setupLifecycle`

An optional `setup` hook on a transaction handler or event processor
registration runs one-time initialisation. **Setup must be idempotent** — it
may run more than once (e.g. on re-deploy).

When it runs is controlled by `setupLifecycle` in the `workflow-engine` config
section:

- `boot` (default) — hooks run during `start()`, after the connection is
  established. No platform auth context is in scope, so hosted-binding calls
  inside setup will not be authorised as a specific user.
- `deferred` — hooks do not run at boot. They run when the platform dispatches
  a setup trigger during deploy, carrying an auth reference for the deploying
  user so hosted-binding calls inside setup are properly authorised.

The platform writes this field into the rendered config for deployments that
support deploy-time setup triggering; you normally don't set it by hand.

`client.setup()` runs all hooks and returns without connecting — useful as an
init-container or migration step.

## Error handling

Return the appropriate `ActionResult` from transaction handler actions:

- `ActionResult.complete()` — move to `nextStage` (or `withCustomStage(...)`)
- `ActionResult.waiting()` — stay in the stage (optionally `withDeadline(...)`)
- `ActionResult.transientError(e)` — the engine retries the transaction
- `ActionResult.fixableError(e)` — error recorded, awaiting a fixed input
- `ActionResult.hardFailure(e)` — divert to `failureStage`, storing the error
  (and any `withErrorData(...)`) in the flow state

Throwing from a handler produces per-transaction errors for the whole batch.

The client automatically handles WebSocket disconnection, reconnection with
exponential backoff, handler re-registration on reconnect, and connection
health monitoring (ping/pong heartbeat).

## Testing

- `./gradlew test` — unit tests.
- `./gradlew componentTest` — conformance tests against a live workflow engine
  (shares workflow fixtures with the TypeScript SDK's component suite). See
  [src/componentTest/README.md](src/componentTest/README.md).

## Samples

- [`samples/workflow-engine-getting-started`](../samples/workflow-engine-getting-started)
  — a minimal Spring Boot provider with a transaction handler and an event
  processor.
