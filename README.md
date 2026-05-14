# Kaleido Java SDK

Java SDK for integrating with the [Kaleido Workflow Engine](https://kaleido.io). Provides both client mode (SDK dials out to the engine) and server mode (engine dials in to the SDK), a Spring Boot starter for zero-boilerplate integration, and GraalVM native-image support.

## Install

Artifacts are published to GitHub Packages. You need a GitHub PAT with `read:packages` scope.

### Gradle (Kotlin DSL)

Add to `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=ghp_YOUR_GITHUB_PAT
```

Add the repository and dependency to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/kaleido-io/kaleido-sdk-java")
        credentials {
            username = findProperty("gpr.user") as String? ?: ""
            password = findProperty("gpr.key") as String? ?: ""
        }
    }
}

dependencies {
    // Spring Boot users (recommended):
    implementation("io.kaleido:workflow-engine-sdk-spring-boot-starter:0.1.0")

    // Plain Java (no Spring):
    implementation("io.kaleido:workflow-engine-sdk:0.1.0")
}
```

### Maven

Add to `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>ghp_YOUR_GITHUB_PAT</password>
  </server>
</servers>
```

Add the repository and dependency to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/kaleido-io/kaleido-sdk-java</url>
  </repository>
</repositories>

<dependency>
  <groupId>io.kaleido</groupId>
  <artifactId>workflow-engine-sdk-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Quickstart (Spring Boot)

1. Add the starter dependency (see above).

2. Configure `application.yaml`:

```yaml
kaleido:
  workflow-engine:
    providerName: my-provider
    url: https://my-account.kaleido.io/endpoint/my-env/my-wfe/rest
    auth:
      type: basic
      username: ${WFE_KEY_NAME}
      password: ${WFE_KEY_VALUE}
```

3. Implement a transaction handler:

```java
@KaleidoTransactionHandler("my-handler")
public class MyHandler implements TransactionHandler {

    @Override
    public String name() { return "my-handler"; }

    @Override
    public WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) {
        var results = request.transactions().stream()
                .map(txn -> WSHandleTransactionResult.stage("complete"))
                .toList();
        return WSHandleTransactionsResult.forRequest(request, results);
    }
}
```

4. Run your Spring Boot app. The SDK connects automatically, registers your handlers, and manages the WebSocket lifecycle.

## Server mode

For hosted providers where the engine dials in to your service, configure server mode instead of a URL:

```yaml
kaleido:
  workflow-engine:
    providerName: my-provider
    server:
      address: 0.0.0.0
      port: 9876
      heartbeatInterval: 15s
      tls:
        enabled: true
        certFile: /etc/ssl/cert.pem
        keyFile: /etc/ssl/key.pem
        caFile: /etc/ssl/ca.pem
        clientAuth: true
        requiredDnAttributes:
          CN: workflow-engine
```

The WS upgrade path is hard-coded to `/ws` to match the Go SDK. There is no bearer-token auth at the WS upgrade; mTLS via `tls.clientAuth` + `tls.requiredDnAttributes` is the supported identity boundary. Setting both `url` and `server` in the same config fails with `KA150050`. The same handlers work in either mode.

## Configuration reference

All properties under `kaleido.workflow-engine`. Wire-canonical keys are camelCase; Spring's relaxed binding also accepts kebab-case in `application.yaml`, but the file-mode `WFE_CONFIG_FILE` path only accepts camelCase.

| Property | Default | Description |
|----------|---------|-------------|
| `providerName` | (required) | Name registered with the engine |
| `providerMetadata` | | Optional JSON blob advertised in `WSRegisterProvider` |
| `url` | | Engine URL (outbound mode); mutually exclusive with `server` |
| `auth.type` | `token` | Auth type: `token`, `basic` |
| `auth.token` | | Token value |
| `auth.header` | `Authorization` | Header name for token auth |
| `auth.username` | | Basic auth username |
| `auth.password` | | Basic auth password |
| `server.address` | `0.0.0.0` | Bind address (server mode); mutually exclusive with `url` |
| `server.port` | (required for server mode) | Listen port |
| `server.heartbeatInterval` | `15s` | WebSocket ping interval |
| `server.requestsPerSecond` | `0` (no throttle) | Per-connection rate limit |
| `server.burst` | `0` (library default) | Token-bucket burst size |
| `server.readBufferSize` | `0` (library default) | WS read buffer |
| `server.writeBufferSize` | `0` (library default) | WS write buffer |
| `server.tls.enabled` | `false` | Terminate TLS on the listener |
| `server.tls.certFile` | | Server certificate (PEM) |
| `server.tls.keyFile` | | Server private key (PEM) |
| `server.tls.caFile` | | CA bundle for verifying client certs (mTLS) |
| `server.tls.clientAuth` | `false` | Require and verify a client cert (mTLS) |
| `server.tls.requiredDnAttributes` | | Subject DN attributes the client cert must contain |
| `retryDelay` | `1s` | Initial reconnect delay (outbound mode) |
| `maxRetries` | `0` (infinite) | Max reconnect attempts (outbound mode) |
| `heartbeatInterval` | `30s` | Client-side ping interval (outbound mode) |
| `pongTimeout` | `10s` | Max time to wait for pong (outbound mode) |
| `resultTimeout` | `2m` | Timeout for engine API responses |

The `WFE_CONFIG_FILE` environment variable can point to a standalone YAML file, matching the TypeScript and Go SDK behaviour.

## Samples

- **[hello-world](samples/hello-world/)** -- Minimal Spring Boot app with a transaction handler and event processor. Start here.
- **[erc20](samples/erc20/)** -- Multi-stage ERC-20 transfer using `StageDirector`, async transaction submission, and result correlation.

## Modules

| Artifact | Description |
|----------|-------------|
| `workflow-engine-sdk` | Core library: client, server, protocol, handlers, stage director |
| `workflow-engine-sdk-spring-boot-starter` | Auto-configuration, `@KaleidoTransactionHandler` / `@KaleidoEventProcessor` annotations, health indicator, Micrometer metrics |

## Support matrix

| Requirement | Version |
|-------------|---------|
| JDK | 21+ |
| Spring Boot | 3.x |
| GraalVM native-image | 21+ |

## Development

```bash
./gradlew build                    # compile + test
./gradlew publishToMavenLocal      # install to ~/.m2
./gradlew javadoc                  # generate Javadoc HTML
```

