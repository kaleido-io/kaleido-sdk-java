# Kaleido Java SDKs

This repository contains Kaleido's Java SDK modules for interacting with Kaleido platform
services. Using these SDKs you can write standalone applications that connect to the Kaleido
platform, or hosted applications that run inside it. The
[Kaleido TypeScript SDKs](https://github.com/kaleido-io/kaleido-sdk-typescript) are the
sibling of this repository — both share the same wire protocol, configuration files, and
programming model.

## Modules

- [`workflow-engine-sdk`](./workflow-engine-sdk/README.md) — build workflow engine
  providers: transaction handlers, event sources, and event processors.

## Quick start

```java
import io.kaleido.workflowengine.sdk.client.WorkflowEngineClient;

var client = WorkflowEngineClient.fromConfigFile();
client.transactionHandler("my-handler", myHandler)
      .start();
```

See the [workflow engine SDK README](./workflow-engine-sdk/README.md) for the configuration
model, handler types, and complete examples, and
[`samples/workflow-engine-getting-started`](./samples/workflow-engine-getting-started) for a
runnable provider.

## Installation

Artifacts are published to **GitHub Packages** (Maven).

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/kaleido-io/kaleido-sdk-java")
    }
}

dependencies {
    implementation("io.kaleido:workflow-engine-sdk:<version>")
}
```

### Maven

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/kaleido-io/kaleido-sdk-java</url>
  </repository>
</repositories>

<dependency>
  <groupId>io.kaleido</groupId>
  <artifactId>workflow-engine-sdk</artifactId>
  <version>_VERSION_</version>
</dependency>
```

## Build

Requires JDK 21.

```bash
./gradlew build                # compile + run unit tests
./gradlew componentTest        # conformance suite against a live workflow engine
./gradlew publishToMavenLocal  # install to ~/.m2 for local testing
./gradlew javadoc              # generate Javadoc HTML
```

## Releasing

Releases are cut by the [Release workflow](.github/workflows/release.yaml), triggered manually
(`workflow_dispatch`). One run does everything for the given version:

1. Builds and tests the SDK with the supplied version.
2. Publishes the Maven artifacts (jar + sources + javadoc + POM) to GitHub Packages.
3. Publishes Javadoc to the `gh-pages` branch under `docs/<version>/`.
4. Creates a GitHub Release with tag `v<version>` and auto-generated release notes.

The `sdkVersion` input drives everything; a leading `v` is stripped automatically, so
`v1.0.0` and `1.0.0` are equivalent. The published Maven version and the Git tag both
derive from it.

### Cut a release candidate (RC)

**From the GitHub UI:**
1. Go to **Actions → Kaleido Workflow Engine Java SDK Release → Run workflow**.
2. Set `sdk_version` (e.g. `v0.1.0-rc.1`)
3. Tick `prerelease` (if this is a pre-release)
4. Run. This publishes `io.kaleido:workflow-engine-sdk:0.1.0-rc.1` and tags `v0.1.0-rc.1`.

## CI

- [`ci.yaml`](.github/workflows/ci.yaml) — builds and unit-tests the SDK on every pull request.
- [`codeql.yaml`](.github/workflows/codeql.yaml) — CodeQL security analysis on PRs and pushes to `main`.
