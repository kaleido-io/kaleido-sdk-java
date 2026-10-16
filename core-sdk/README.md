# Kaleido core SDK

Connectivity shared by Kaleido's Java SDKs.
Typed clients for platform services build on it.

Two packages:

- `io.kaleido.sdk.core.config`: the `service-bindings` config model (`ServiceBindingConfig`,
  `ServiceBindingAuth`) and its parser, `ServiceBindings`. A binding is `hosted` (reached
  through the provider-proxy) or `non-hosted` (a URL and auth).
- `io.kaleido.sdk.core.http`: calling a service, through a binding or directly.
  - `ServiceClientOptions` says how to reach one binding: `forBinding` resolves a binding
    to them, and `fromConfig` resolves one by name from a config file.
  - `ServiceClient` calls the service with JSON bodies, over HTTP or through a
    `ServiceProxy`, with the same code either way. Extend it for a typed client:

```java
class AssetManagerClient extends ServiceClient {
    AssetManagerClient(ServiceClientOptions options) { super(options); }

    Asset asset(String id) { return get("/api/v1/assets/" + id, Asset.class); }
}
```

### Getting options

A typed client takes `ServiceClientOptions`. Get them one of three ways:

```java
// 1. From a binding in the Kaleido config file (KALEIDO_CONFIG_FILE, or a path you pass).
//    Non-hosted bindings only.
new AssetManagerClient(ServiceClientOptions.fromConfig("asset-manager"));

// 2. Inside a workflow engine provider: hosted or non-hosted, as the request's user.
new AssetManagerClient(client.getServiceClientOptions("asset-manager", txn.authRef()));

// 3. Directly, with no binding.
new AssetManagerClient(new ServiceClientOptions.Http(
        "https://my-asset-manager.example.com/rest", ServiceBindingAuth.basic("user", "api-key"),
        3 /* maxRetries */, 30_000 /* timeout ms */));
```

Resolving a binding means looking up its name under `service-bindings` (top level, else
under `workflow-engine`) and turning it into options. A non-hosted binding becomes direct
HTTP to its `url` with its `auth`. A hosted binding goes through the provider-proxy, so it
needs the connected provider in option 2.

### Install

```kotlin
implementation("io.kaleido:core-sdk:<version>")
```
