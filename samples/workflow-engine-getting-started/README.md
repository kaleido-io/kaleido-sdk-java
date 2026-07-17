# Workflow Engine getting-started sample

A minimal, buildable Spring Boot provider that registers two handlers with a
Kaleido Workflow Engine:

- **`hello`** — a `TransactionHandler` that logs each transaction dispatched
  to it and completes it into the `complete` stage.
- **`echo`** — an `EventProcessor` that logs each event batch it receives.

## The mental model

This sample is not a standalone application, but is a
**provider** that can be leveraged by workflows: a small Java process that dials the engine over a WebSocket and
registers named handlers. The engine dispatches transactions to a
`TransactionHandler` and event batches (via a **stream**) to an
`EventProcessor`.

The workflow definition (stages, transitions, handler bindings) lives on the
platform as YAML posted to the Workflow Engine's REST API. A sample
workflow can be seen in the [component test](src/componentTest/resources).

See [`src/main/java/io/kaleido/workflowengine/sample/SampleProviderApp.java`](src/main/java/io/kaleido/workflowengine/sample/SampleProviderApp.java)
for how the two handlers are registered, and
[`HelloTransactionHandler.java`](src/main/java/io/kaleido/workflowengine/sample/HelloTransactionHandler.java) /
[`EchoEventProcessor.java`](src/main/java/io/kaleido/workflowengine/sample/EchoEventProcessor.java)
for the handlers themselves.

## Build

This is a standalone Gradle project — not part of the SDK build.

```bash
# Against the published SDK (needs a GitHub Packages PAT with read:packages)
GITHUB_ACTOR=<github-username> GITHUB_TOKEN=<pat> ./gradlew build

# Against the in-repo SDK checkout (this repo, for local development)
./gradlew -PsdkIncludeBuild=true build
```

The `-PsdkIncludeBuild=true` flag substitutes `io.kaleido:workflow-engine-sdk`
with a composite build of `../../` (the SDK repo root) — useful before a
release is published, or when developing the SDK and sample together.

## Run locally

You need a Workflow Engine to connect to — then you can update the sample config and run the sample provider locally:

```bash
cp src/main/resources/wfe-config.yaml.sample /tmp/wfe-config.yaml
# edit /tmp/wfe-config.yaml: set url/auth for your Workflow Engine
KALEIDO_CONFIG_FILE=/tmp/wfe-config.yaml ./gradlew -PsdkIncludeBuild=true bootRun
```

You should see the provider connect and register:

```
HandlerRuntime : WebSocket connected to ws://<host>/ws
HandlerRuntime : Registering provider and handlers provider=getting-started-sample transactionHandlers=1 eventSources=0 eventProcessors=1
```

Once connected, `hello` and `echo` can be referenced from workflow
definitions and streams bound to provider `getting-started-sample`.

## Verify

`src/componentTest` proves the two handlers actually work against a live
engine:

- **`HelloTransactionTest`** posts the workflow in
  `workflows/hello.yaml` (one `pending` stage bound to `hello`), submits a
  transaction, and confirms it reaches the `complete` stage that
  `HelloTransactionHandler` replies with.
- **`EchoEventProcessorTest`** registers `echo` alongside a throwaway
  `feeder` event source on one connection, drives a stream binding
  `feeder -> echo`, and confirms `EchoEventProcessor` actually logged the
  dispatched batch (captured with a Logback `ListAppender` — the shipped
  handler is exercised unmodified).

With a Workflow Engine reachable:

```bash
./gradlew -PsdkIncludeBuild=true componentTest
```

`componentTest` is not part of `check`, so `./gradlew build` stays green
without a running engine. See [`src/componentTest`](src/componentTest) to
point it at your engine (env vars or `test-config.yaml`); it defaults to
`http://localhost:5503` with token auth.

## Container build (GraalVM native-image)

The provider can also be run as a hosted provider service in the Kaleido platform.
The [`Dockerfile`](Dockerfile) builds a native-image binary in a
`ghcr.io/graalvm/native-image-community:21` builder stage and copies it into
a distroless runtime image — no JVM in the final image. Build it directly
(needs a GitHub Packages PAT with `read:packages`, passed as a BuildKit
secret):

```bash
docker build --secret id=gpr_token,src=<path-to-file-containing-your-PAT> -t provider .
```

[`Dockerfile.local`](Dockerfile.local) can be used for building before
`workflow-engine-sdk` has a published release: it builds against the in-repo
SDK checkout instead (composite build), so it needs no PAT. Build it with the
**`kaleido-sdk-java/` repo root as context**, since the composite build needs
the whole SDK source tree:

```bash
cd ../.. && docker build -f samples/workflow-engine-getting-started/Dockerfile.local -t provider .
```

Or compile the native binary directly with Gradle (requires a GraalVM 21
toolchain):

```bash
./gradlew -PsdkIncludeBuild=true nativeCompile
./build/native/nativeCompile/provider
```

Native-image needs reachability metadata for anything reflectively
(de)serialized. Spring Boot's AOT processing covers this sample's own two
handlers, and the SDK ships its own `reflect-config.json` for its protocol
types. If you extend the sample with your own records/DTOs bound to the
SDK's JSON (de)serialization, add `@RegisterReflectionForBinding` (or a
`RuntimeHintsRegistrar`) for those types, then confirm the workflow engine actually
logs `Provider '<name>' registered` — the provider's own "Registering..."
log line only proves it sent the message, not that the engine parsed it.

## Deploy to a live Kaleido environment

A hosted deployment can be configured with a few steps on the Kaleido platform, using
the platform's Artifact registry, Provider proxy, and Provider service
("Hosted providers" in the Kaleido docs). This is the condensed path for
this sample.

1. **Build for the target platform.** Match your cluster's node architecture
   (usually `linux/amd64`, not arm64 or anything else):

   ```bash
   cd ../.. && docker buildx build --platform linux/amd64 \
     -f samples/workflow-engine-getting-started/Dockerfile.local \
     -t <registry-host>/<namespace>/<repo>:<tag> --push .
   ```

2. **Push it to your environment's Artifact Registry.** Create the namespace
   and repository in the console first if they don't exist
   (Artifact Registry service → Namespaces). `docker login <registry-host>`
   with a platform API key before the `--push` above.

3. **Create a Provider service** (Services → Create service → Provider):
   pick the namespace/repository/tag you just pushed. No custom provider config is needed.

4. **Confirm it connects.** The Provider proxy's Providers page should show
   `CONNECTED`. The assigned provider name is the service ID with colons
   replaced by dashes (e.g. service `s:egjbd6ijds` registers as
   `s-egjbd6ijds`).

5. **Bind a workflow to it.** Use [`src/componentTest/resources/workflows/hello.yaml`](src/componentTest/resources/workflows/hello.yaml)
   as a template, with `handlerBindings.hello.provider` set to your provider
   name from step 4. Create a new Workflow from this YAML, then `POST /api/v1/transactions` with
   `{"workflowId": "<id>", "operation": "greet", "input": {}}`. It should
   reach `stage: complete`.

6. **For `echo`**, bind an event stream's `eventProcessor.handler.provider`
   the same way (`eventProcessor: {type: handler, handler: {name: echo,
   provider: <provider name>}}`), matching the shape in
   [`EchoEventProcessorTest`](src/componentTest/java/io/kaleido/workflowengine/sample/componenttest/EchoEventProcessorTest.java).
