# Upgrading

## From 1.0.0-rc.2

The service-binding classes moved from `workflow-engine-sdk` into the new `core-sdk` module, so
clients other than workflow engine providers can use them. `workflow-engine-sdk` depends on
`core-sdk`, so no dependency changes are needed: update the imports and recompile.

| 1.0.0-rc.2 | Now |
|---|---|
| `io.kaleido.workflowengine.sdk.service.ServiceBindingAuth` | `io.kaleido.sdk.core.config.ServiceBindingAuth` |
| `io.kaleido.workflowengine.sdk.service.ServiceBindingConfig` | `io.kaleido.sdk.core.config.ServiceBindingConfig` |
| `io.kaleido.workflowengine.sdk.service.ServiceBindings` | `io.kaleido.sdk.core.config.ServiceBindings` |
| `io.kaleido.workflowengine.sdk.service.ServiceClientOptions` | `io.kaleido.sdk.core.http.ServiceClientOptions` |

The classes are otherwise unchanged, and so are the methods that return them:
`WorkflowEngineClient.getServiceBindings()`, `getServiceBinding(name)`,
`getServiceClientOptions(...)` and `SetupContext.getServiceClientOptions(name)`.

Also changed:

- `ServiceClientOptions.WsProxy` takes a `ServiceProxy`, the interface `WSProxyAdapter` now
  implements, in place of a `WSProxyAdapter`. Code that passes a `WSProxyAdapter` still compiles;
  code that calls `wsProxy().request(...)` on the record should call `send(...)`, or use
  `ServiceClient`, which does it for you.
- A request through the provider-proxy that comes back with an error completes with a
  `ServiceProxyException`, which carries the response `status()`, in place of a plain
  `RuntimeException`. It extends `RuntimeException`, so existing `catch` blocks still apply.
- A `service-bindings` entry names its service type with `serviceType`. `type` is still read
  when `serviceType` is absent.
