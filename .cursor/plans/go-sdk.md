I'm porting the Kaleido Workflow Engine SDK to Java. The existing Go SDK
already supports both modes; I need to confirm the server/hosted-provider
mode design before implementing it in Java. Please answer each, with file
paths and code snippets where possible.

1. Does the Go workflow-engine SDK have a "server mode" / "hosted provider"
   mode where the engine dials *into* the SDK over WebSocket (as opposed to
   the SDK dialing the engine)? If yes, what is it called in the code
   (struct/function/package names) and which repo + path is it in?

2. What does the YAML configuration look like for that mode? In particular,
   what goes under the `workflow-engine.server:` block? Show a complete
   example.

3. When the engine dials into the provider, what is the handshake?
     a. Does the *engine* send WSRegisterProvider / WSRegisterHandler, or
        does the *provider* still send them once the connection is up?
     b. If the provider sends them, how does it know which providerName
        and handlers to advertise, given the YAML in server mode has no
        providerName under url/auth?
     c. What protocol-version / capability negotiation happens, if any?

4. How does authentication work for inbound connections from the engine
   to the provider?
     - Is it bearer-token validated by the provider? mTLS? Signed JWT
       issued by the Kaleido control plane?
     - What header(s) does the engine send?
     - Does the SDK validate the engine identity, or trust the network?

5. Where is the Go WebSocket *server* implementation that providers use
   (i.e., the Go equivalent of what we need to write in Java)? Library
   used (gorilla/websocket, nhooyr.io/websocket, etc.), main file path,
   ~50-line excerpt of how it accepts a connection and bridges it to the
   handler runtime would be ideal.

6. Are there any protocol or behavioural differences in server mode vs
   client mode beyond direction-of-dial? e.g.:
     - Different WSMessageType values used?
     - Different batching, retry, or idempotency semantics?
     - Ping/pong direction (who pings whom)?
     - Reconnect semantics — in server mode the provider can't reconnect,
       it can only wait; how does the engine handle re-dial?

7. Is there a reference sample / integration test in the Go repo that
   runs a provider in server mode end-to-end against the engine? Path
   please. That would be the cheapest source of truth for the protocol.

8. Anything you would warn me about porting this to Java that the Go
   implementation learned the hard way?

---

Here are answers to each question, based on reading the SDK and the engine wshandler in this repo.

## 1. Is there a "server mode" / "hosted provider" mode?

Yes. The SDK supports both directions of dial. There are two `HandlerRuntime` constructors selected via `HandlerRuntimeConfig`:

- **Outbound (default)**: SDK dials engine. Entry point `NewHandlerRuntimeOutboundWS` / `NewHandlerRuntimeOutboundWSWithMetadata`.
- **Server / inbound (hosted provider)**: SDK runs an HTTP+WS server, engine dials in. Entry points `NewHandlerRuntimeServerWrapper` (full http.Server) or `WSUpgradeHandler` (handler you mount on your own http.Server) plus `NewHandlerRuntimeInboundWSWithMetadata`.

Selector lives in [workflow-engine/pkg/enginesdk/handler_runtime.go](workflow-engine/pkg/enginesdk/handler_runtime.go):

```134:169:workflow-engine/pkg/enginesdk/handler_runtime.go
func NewHandlerRuntime(ctx context.Context, conf *HandlerRuntimeConfig, handlerSets ...HandlerSet) (HandlerRuntime, error) {
	if conf == nil {
		conf = &HandlerRuntimeConfig{}
	}
	if os.Getenv(EnvWorkflowEngineConfigFile) != "" || strings.TrimSpace(conf.ConfigFile) != "" {
		loadedConf, err := BuildHandlerRuntimeConfigFromFile(ctx, conf.ConfigFile)
		// ...
		conf = loadedConf
	}
	// ...
	switch {
	case conf.OutboundWS != nil && conf.Server != nil:
		return nil, i18n.NewError(ctx, engmsgs.MsgSDKConfigMutuallyExclusive)
	case conf.OutboundWS != nil:
		return NewHandlerRuntimeOutboundWSWithMetadata(ctx, conf.ProviderName, conf.ProviderMetadata, conf.OutboundWS, handlerSets...)
	case conf.Server != nil:
		return NewHandlerRuntimeServerWrapper(ctx, *conf.Server, conf.ProviderName, conf.ProviderMetadata, handlerSets...)
	default:
		return nil, i18n.NewError(ctx, engmsgs.MsgSDKConfigNeitherSet)
	}
}
```

Engine-side counterpart that dials *into* the provider: [`outboundConnection`](workflow-engine/pkg/wshandler/outbound_conn.go) in `workflow-engine/pkg/wshandler/`. The engine's `wshandler.WebSocketServer` actually owns BOTH inbound (providers dialing the engine) and outbound (engine dialing providers) connections in the same struct — see `outboundConnections []*outboundConnection` in [workflow-engine/pkg/wshandler/wsserver.go](workflow-engine/pkg/wshandler/wsserver.go).

The naming convention is: from the SDK's perspective `OutboundWS` = SDK→engine; `Server` = engine→SDK. From the engine's perspective `InboundWebSocket` = SDK→engine (default); `OutboundWebSocket` = engine→SDK (hosted provider). They invert.

## 2. YAML config

Server mode SDK YAML lives under the `workflow-engine:` root section. The config init is in [`workflow-engine/pkg/enginesdk/config.go`](workflow-engine/pkg/enginesdk/config.go):

```39:53:workflow-engine/pkg/enginesdk/config.go
func InitConfig(configSection config.Section) {
	configSection.AddKnownKey(ConfigWorkflowEngineProviderName)
	configSection.AddKnownKey(ConfigWorkflowEngineProviderMetadata)
	wsclient.InitConfig(configSection)

	serverSection := configSection.SubSection("server")
	serverSection.AddKnownKey(ConfigServerAddress)
	serverSection.AddKnownKey(ConfigServerPort)
	serverSection.AddKnownKey(ConfigServerReadBufferSize, 0)
	serverSection.AddKnownKey(ConfigServerWriteBufferSize, 0)
	serverSection.AddKnownKey(ConfigServerHeartbeatInterval, "15s")
	serverSection.AddKnownKey(ConfigServerThrottleRPS, 0)
	serverSection.AddKnownKey(ConfigServerThrottleBurst, 0)
	fftls.InitTLSConfig(serverSection.SubSection("tls"))
}
```

Complete server-mode example (the path the engine dials is hard-coded to `/ws` — see `r.Path("/ws").HandlerFunc(...)` in `NewHandlerRuntimeServerWrapper`):

```yaml
workflow-engine:
  providerName: my-provider
  providerMetadata:        # optional, advertised in the WSRegisterProvider message
    displayName: "My Provider"
    version: "1.0.0"
  server:
    address: 0.0.0.0
    port: 9876
    readBufferSize: 0      # 0 = gorilla default (4KB)
    writeBufferSize: 0
    heartbeatInterval: 15s
    requestsPerSecond: 0   # token-bucket on per-conn send rate, 0 = no throttle
    burst: 0
    tls:
      enabled: false       # set true and configure caFile/certFile/keyFile + clientAuth for mTLS
```

Outbound mode (for comparison) uses `workflow-engine.ws.url` / `workflow-engine.url` (the standard `wsclient.InitConfig` keys) and *no* `server:` block. Setting both is rejected by `MsgSDKConfigMutuallyExclusive`.

Engine YAML for the corresponding dial:

```yaml
providerConnections:
- name: my-provider
  endpoint:
    ws:
      url: ws://provider-host:9876/ws
    # auth, tls, retry etc. all standard wsclient/ffresty config
```

A live example is in [`common/connector-toolkit/wfe-conf.yaml`](common/connector-toolkit/wfe-conf.yaml) and the test harness at [`workflow-engine/pkg/enginetest/workflow_engine.go`](workflow-engine/pkg/enginetest/workflow_engine.go) (look for `te.outboundConnections`).

## 3. Handshake

**a) Provider sends `WSRegisterProvider` and `WSRegisterHandler` in BOTH directions of dial.** The engine never sends those — it only consumes them. Same code path runs whether the connection is dialed by the SDK or accepted by the SDK:

```385:414:workflow-engine/pkg/enginesdk/handler_runtime.go
func (hr *handlerRuntime) Start(ctx context.Context, wsc wsclient.WSClient) error {
	hr.feConn = wsc
	return hr.wsRegisterAndStart(ctx, wsc)
}

func (hr *handlerRuntime) wsRegisterAndStart(ctx context.Context, wsc wsclient.WSClient) error {
	err := hr.registerProvider(ctx, wsc)
	for _, h := range hr.handlers {
		if lh, isHandler := h.(EventSource); err == nil && isHandler {
			err = hr.registerListener(ctx, wsc, lh)
		}
		if lh, isHandler := h.(EventProcessor); err == nil && isHandler {
			err = hr.registerEventProcessor(ctx, wsc, lh)
		}
		if rh, isHandler := h.(TransactionHandler); err == nil && isHandler {
			err = hr.registerTransactionHandler(ctx, wsc, rh)
		}
	}

	if err == nil {
		hr.connected.Store(true)
		if hr.wsLoopDone == nil {
			hr.wsLoopDone = make(chan struct{})
			go hr.wsReceiveLoop(ctx, wsc)
		}
	}
	return err
}
```

`Start` is invoked by `WSUpgradeHandler` after the upgrade completes; the outbound runtime invokes the same function as the wsclient `afterConnect` callback.

**b) The provider knows its name and handlers from local config.** `providerName` comes from `workflow-engine.providerName` (the same key used in outbound mode). Handlers are whatever you pass to `HandlerSetFor(...)` / the Server constructors. The engine config (`providerConnections[].name`) MUST match what the SDK sends — if they differ the engine rejects the connection:

```135:150:workflow-engine/pkg/wshandler/wsconn.go
case engtypes.WSMessageTypeRegisterProvider:
    var registerMsg engtypes.WSRegisterProvider
    err = json.Unmarshal(msgData, &registerMsg)
    if err == nil && c.providerName != "" && registerMsg.ProviderName != c.providerName {
        err = i18n.NewError(c.ctx, engmsgs.MsgWSDuplicateProviderRegistration, c.providerName, registerMsg.ProviderName)
    }
```

For inbound (SDK→engine) `c.providerName` is empty initially and gets set from the message; for outbound (engine→SDK) it's pre-populated from `providerConnections[].name`. So in server mode the engine *does* validate the name match.

**c) No protocol-version or capability negotiation.** No version field, no capabilities exchange. Just `WSRegisterProvider` first (mandatory), then `WSRegisterHandler` per handler. The engine enforces ordering: a handler register before provider register triggers `MsgWSMissingProviderRegistration` and the connection is closed after sending a `WSMessageTypeProtocolError` envelope (with a 250ms grace pause).

The full message-type enum is in [`workflow-engine/pkg/engtypes/websocket_handlers.go`](workflow-engine/pkg/engtypes/websocket_handlers.go) and contains all 16 values you'll need.

## 4. Authentication

**Inbound from engine to provider**:

- **No bearer token, no JWT, no header check on the SDK side.** Look at `WSUpgradeHandler` — it does a raw `wsUpgrader.Upgrade(w, r, nil)` with no inspection of headers:

```68:89:workflow-engine/pkg/enginesdk/handler_runtime.go
func WSUpgradeHandler(ctx context.Context, wsUpgrader *websocket.Upgrader, wsWrapConf wsclient.WSWrapConfig, providerName string, providerMetadata *fftypes.JSONObject, handlerSets ...HandlerSet) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var conn *websocket.Conn
		var sc wsclient.WSClient
		hr, err := NewHandlerRuntimeInboundWSWithMetadata(ctx, providerName, providerMetadata, handlerSets...)
		if err == nil {
			conn, err = wsUpgrader.Upgrade(w, r, nil)
		}
		if err == nil {
			sc = wsclient.Wrap(ctx, wsWrapConf, conn, func() {})
			err = hr.Start(ctx, sc)
		}
		// ... error path writes a JSON RESTError with 500
	}
}
```

- **TLS / mTLS is the recommended boundary.** The `server.tls` config uses `fftls` `ServerType`, so set `clientAuth: true` + `requiredDNAttributes` etc. for mTLS. That's the only built-in identity check the SDK supports for inbound engine connections.

- **What headers the engine sends:** whatever the operator put in `providerConnections[].endpoint.*` config (standard `wsclient` config: `auth.username/password` for basic, custom `httpHeaders`, TLS client cert, etc.). Nothing protocol-specific.

- **In-band auth tokens** are a separate layer. Each `WSEnvelope` can carry `authTokens map[string]string` (per-`authRef`) injected by the engine's `AuthProvider` if the provider's `SocketAuthContext.AllowAuthTokenPropagation` returns true. For outbound (engine→SDK) this is currently always true via `kaleidoSocketAuthContext.isOutbound`:

```153:158:workflow-engine/pkg/kldplugins/auth_provider.go
func (kap *kaleidoAuthProvider) GetOutboundSocketAuthContext(ctx context.Context) engplugins.SocketAuthContext {
	return &kaleidoSocketAuthContext{
		isOutbound: true,
	}
}
```

Note the comment in the same file: `TODO: This is a migration compatibility feature for now. Needs to be reconsidered when we have outbound connections to customer code containers.` — i.e. they themselves know in-band token forwarding to third-party providers is sketchy.

- **The SDK does not validate the engine identity at the TCP/HTTP layer** beyond TLS. It trusts the network.

## 5. Go WebSocket server implementation

Library: `github.com/gorilla/websocket` (and `github.com/gorilla/mux` for routing). The server-mode bridge code is `WSUpgradeHandler` (above, lines 68-89 of `handler_runtime.go`) for the upgrade itself.

The full hosted server (HTTP listener + `/ws` route + TLS) is `NewHandlerRuntimeServerWrapper`:

```196:228:workflow-engine/pkg/enginesdk/handler_runtime.go
func NewHandlerRuntimeServerWrapper(ctx context.Context, serverConfig HandlerRuntimeServerConfig, providerName string, providerMetadata *fftypes.JSONObject, handlerSets ...HandlerSet) (HandlerRuntime, error) {
	if serverConfig.Upgrader == nil {
		serverConfig.Upgrader = defaultWSUpgrader()
	}
	r := mux.NewRouter()
	r.Path("/ws").HandlerFunc(WSUpgradeHandler(ctx, serverConfig.Upgrader, serverConfig.WSWrapper, providerName, providerMetadata, handlerSets...))
	addr := fmt.Sprintf("%s:%d", serverConfig.Address, serverConfig.Port)
	tlsEnabled := serverConfig.TLSConfig != nil
	log.L(ctx).Infof("Inbound WS server starting on %s (TLS=%v)", addr, tlsEnabled)

	srv := &http.Server{
		Addr:      addr,
		Handler:   r,
		TLSConfig: serverConfig.TLSConfig,
	}
	wrapper := &serverWrapper{srv: srv, stopped: make(chan struct{}), ctx: ctx}
	go func() {
		var err error
		defer func() {
			if err != nil && err != http.ErrServerClosed {
				wrapper.pErr.Store(&err)
			}
			close(wrapper.stopped)
		}()
		if serverConfig.TLSConfig != nil {
			err = srv.ListenAndServeTLS("", "")
		} else {
			err = srv.ListenAndServe()
		}
		logListenerShutdown(ctx, err)
	}()
	return wrapper, nil
}
```

After upgrade, the connection is wrapped in `wsclient.Wrap` (firefly-common's WS wrapper that adds heartbeat ping/pong, throttling, send/receive channels, panic-safe close). The bridge to handlers is `wsReceiveLoop`:

```439:467:workflow-engine/pkg/enginesdk/handler_runtime.go
func (hr *handlerRuntime) wsReceiveLoop(ctx context.Context, wsc wsclient.WSClient) {
	defer close(hr.wsLoopDone)

	log.L(ctx).Infof("WebSocket receive loop started")

	for b := range wsc.Receive() {
		var header engtypes.WSEnvelope
		err := json.Unmarshal(b, &header)
		switch header.MessageType {
		case engtypes.WSMessageTypeEventSourceConfig:
			err = hr.eventSourceReInitConfig(ctx, b) // synchronous as we need it to happen before next poll
		case engtypes.WSMessageTypeEventSourceValidateConfig:
			go asyncHandleWithResponse(ctx, hr, wsc, b, engtypes.WSMessageTypeEventSourceValidateConfigResult, &engtypes.WSEventSourceValidateConfigResult{}, hr.eventSourceValidateConfig)
		case engtypes.WSMessageTypeEventSourcePoll:
			go asyncHandleWithResponse(ctx, hr, wsc, b, engtypes.WSMessageTypeEventSourcePollResult, &engtypes.WSEventSourcePollResult{}, hr.eventSourcePoll)
		case engtypes.WSMessageTypeEventSourceDelete:
			go asyncHandleWithResponse(ctx, hr, wsc, b, engtypes.WSMessageTypeEventSourceDeleteResult, &engtypes.WSEventSourceDeleteResult{}, hr.eventSourceDelete)
		case engtypes.WSMessageTypeHandleTransactions:
			go asyncHandleWithResponse(ctx, hr, wsc, b, engtypes.WSMessageTypeHandleTransactionsResult, &engtypes.WSHandleTransactionsResult{}, hr.transactionHandlerBatch)
		case engtypes.WSMessageTypeEventProcessorBatch:
			go asyncHandleWithResponse(ctx, hr, wsc, b, engtypes.WSMessageTypeEventProcessorBatchResult, &engtypes.WSEventProcessorBatchResult{}, hr.processEventBatch)
		case engtypes.WSMessageTypeEngineAPISubmitTransactionsResult:
			hr.completeReqIfInflight(ctx, header.ID, b)
		}
		if err != nil {
			log.L(ctx).Errorf("Error processing data from server (%s): %s", err, b)
		}
	}

	log.L(ctx).Infof("WebSocket receive loop ended")
}
```

The panic-safe response helper `asyncHandleWithResponse` is at lines 474-536 of the same file — worth porting verbatim because the engine's roundTrip times out (default 2 min) if the provider doesn't respond.

## 6. Differences server vs client mode beyond direction-of-dial

The wire protocol is **identical**: same `WSMessageType` values, same envelope, same JSON encoding, same handlers. The handshake (`registerProvider` first, then per-handler `registerHandler`) is the same code path on the SDK regardless of who dialed.

What does differ:

- **Receive-loop lifecycle**:
  - Outbound: one `handlerRuntime` is created once, and `wsReceiveLoop` is created once and reused across reconnects (see the `if hr.wsLoopDone == nil` guard at line 406-410). On each reconnect `wsRegisterAndStart` runs again to re-register on the new socket.
  - Server mode: every accepted upgrade creates a fresh `handlerRuntime` via `NewHandlerRuntimeInboundWSWithMetadata`; nothing is shared between connections.
- **Reconnect**:
  - Outbound: `wsclient.New` does the reconnect with `InitialDelay`/`MaximumDelay`/`DelayFactor`. The SDK is the active retrier.
  - Server: the SDK cannot reconnect — it can only accept. The engine's `outboundConnection` does the retry loop:

```61:81:workflow-engine/pkg/wshandler/outbound_conn.go
func (oc *outboundConnection) backgroundConnect(wsc wsclient.WSClient) {
	defer close(oc.bgConnectDone)

	err := oc.retry.Do(oc.ctx, "outbound-ws-connect", func(attempt int) (retry bool, err error) {
		err = wsc.Connect()
		if err != nil {
			oc.connectDebug.Store(&engtypes.ProviderHandlerConnectDebugInfo{
				ConnectionError:       err.Error(),
				ConnectionAttempts:    attempt,
				LastConnectionAttempt: fftypes.Now(),
			})
		}
		return true, err
	})
	// ...
}
```

  In-flight engine→provider requests on the dropped conn get a `MsgWSWebSocketClosed` error so the engine's `roundTrip` callers fail and retry the higher-level operation.

- **Ping/pong direction**: both sides independently. Each end calls `wsclient.Wrap` with its own `HeartbeatInterval`. The wrapper sends WS PING control frames whenever its timer expires and bumps the read deadline to `2 * heartbeatInterval` on each pong. So both peers ping each other. If you set `heartbeatInterval: 0` on the SDK side, the SDK won't ping but will still respond to engine pings.

- **Throttling**: server mode exposes per-connection `requestsPerSecond` / `burst` token-bucket on the WSClient send side (handler_runtime.go lines 113-117). Outbound mode reuses the same wsclient throttle config but it lives under the standard ws config keys.

- **Batching/idempotency**: identical, message-level. Each request carries an `id` (nanoid-10 generated on the engine) and the result reuses that `id`; that's the whole correlation mechanism. No multiplexing-stream-IDs on top of WS.

- **No different `WSMessageType` values are used** — same set in both modes.

## 7. Reference sample / integration test

End-to-end (engine binary + in-process SDK server, dialed by engine):

```27:53:workflow-engine/pkg/enginetest/workflow_engine_test.go
func TestSampleUsage(t *testing.T) {
	ctx := context.Background()

	dummyProviderWS := httptest.NewServer(
		enginesdk.WSUpgradeHandler(context.Background(), &websocket.Upgrader{}, wsclient.WSWrapConfig{},
			"provider1", nil, enginesdk.HandlerSetFor()))
	t.Cleanup(dummyProviderWS.Close)

	// ...
	te, err := NewTestWorkflowEngine(ctx).
		UseRepoDir("../..").
		UseDatabase("localtest.enginetest", "postgres", "my-secret").
		DropRecreateDB().
		// ...
		WithOutboundConnection("provider1", dummyProviderWS.URL).
		// ...
		Start()
	require.NoError(t, err)
```

(Tagged `//go:build componenttest`. Runs the actual workflow-engine binary against a tiny SDK-in-server-mode instance.)

Wire-level test that's the cheapest source of truth for the protocol — it hand-crafts the register messages and the round-trip:

```22:144:workflow-engine/pkg/wshandler/outbound_conn_test.go
func TestOutboundConnOK(t *testing.T) {
	passWS := make(chan (*websocket.Conn))
	ws1 := httptest.NewServer(http.HandlerFunc(func(res http.ResponseWriter, txn *http.Request) {
		upgrader := &websocket.Upgrader{WriteBufferSize: 1024, ReadBufferSize: 1024}
		ws, err := upgrader.Upgrade(res, txn, http.Header{})
		require.NoError(t, err)
		passWS <- ws
	}))
	// ... engine WebSocketServer with OutboundConnections to ws1.URL ...
	wsc := <-passWS

	// from server (provider) to client (engine)
	err = wsc.WriteJSON(&engtypes.WSRegisterProvider{
		WSEnvelope: engtypes.WSEnvelope{MessageType: engtypes.WSMessageTypeRegisterProvider},
		ProviderName: "provider1",
	})
	// ...
	err = wsc.WriteJSON(&engtypes.WSRegisterHandler{
		WSEnvelope: engtypes.WSEnvelope{
			MessageType: engtypes.WSMessageTypeRegisterHandler,
			HandlerType: engtypes.HandlerTypeTransactionHandler,
			Handler:     "handler1",
		},
	})
```

Production reference provider in server mode: [`test-provider/`](test-provider/) (in this repo). It uses `enginesdk.NewHandlerRuntime(...)` with `WFE_CONFIG_FILE` env var to load the `workflow-engine:` YAML — see [`test-provider/pkg/testprovider/provider.go`](test-provider/pkg/testprovider/provider.go) and [`test-provider/hack/wfe-config.yaml`](test-provider/hack/wfe-config.yaml).

Production caller of "engine dials provider": [`provider-proxy/pkg/wsproxy/manager.go`](provider-proxy/pkg/wsproxy/manager.go) — its package doc literally says *"Each provider runtime exposes a websocket server (enginesdk in server mode)"*.

## 8. Things to watch out for in the Java port

1. **`WSEventSourceConfig` is dispatched synchronously** in `wsReceiveLoop`, before any `WSEventSourcePoll` for that stream. Do the same in Java. Engine assumes the next poll will see the new config:

   ```416:437:workflow-engine/pkg/enginesdk/handler_runtime.go
   func (hr *handlerRuntime) eventSourceReInitConfig(ctx context.Context, b []byte) (err error) {
       var listenerConf engtypes.WSEventSourceConfig
       err = json.Unmarshal(b, &listenerConf)
       if err == nil {
           streamKey := listenerConf.StreamID.String()
           hr.listenerConfMux.Lock()
           _, existed := hr.listenerConfs[streamKey]
           hr.listenerConfs[streamKey] = &listenerConf
           hr.listenerConfMux.Unlock()
           if existed {
               for _, h := range hr.handlers {
                   if es, ok := h.(EventSource); ok {
                       es.EventSourceConfigChanged(ctx, streamKey)
                   }
               }
           }
       }
       return err
   }
   ```

2. **Always send a response, even on panic.** `asyncHandleWithResponse` (handler_runtime.go lines 474-536) wraps every poll/validate/handle/process with `defer recover()` and emits a result envelope with the original `id` and `error` field set. The engine's `roundTrip` will time out at 2 minutes (`ConfigResultTimeout` default) if you go silent. Java equivalent: `try/finally` send a result with the same envelope id and `error="..."`.

3. **`WSRegisterProvider` MUST be the very first message.** If you send `WSRegisterHandler` first the engine responds with `WSMessageTypeProtocolError` and closes the conn after a 250ms grace (see [`workflow-engine/pkg/wshandler/wsconn.go`](workflow-engine/pkg/wshandler/wsconn.go) lines 151-194). And if your `providerName` doesn't match the engine-configured `providerConnections[].name`, you get `MsgWSDuplicateProviderRegistration` and a close.

4. **No protocol versioning.** If you bump message shapes you do it via additive JSON fields. There's no negotiation step to lean on.

5. **No auth at the WS handshake on the SDK side.** Don't expect a bearer/JWT to validate; if you want one, do it on your Java HTTP server ahead of the WS upgrade. The standard pattern they use is mTLS on the listener (`server.tls.clientAuth: true`).

6. **Ping/pong mandatory.** If your Java server doesn't respond to PING control frames, the engine's `wsclient.Wrap` will hit its read-deadline (`2 * heartbeatInterval`, default 30s) and tear down. Provider-proxy comments confirm 15s is the agreed default ([`provider-proxy/pkg/wsproxy/manager.go`](provider-proxy/pkg/wsproxy/manager.go) line 38: `defaultProviderHeartbeat = 15 * time.Second // matches enginesdk server default`).

7. **Round-robin per `provider:handlerType:handler` key.** Multiple connections registering the same handler under the same providerName get round-robined (`bindConnectionAndLock` + `wlmCounter` in [wsserver.go](workflow-engine/pkg/wshandler/wsserver.go)). If your Java server accepts >1 dial from the engine (e.g. K8s rolling update with overlap) both will receive traffic; ensure handlers are stateless or use a coordination primitive.

8. **Stream-init state is per-connection**, kept in `webSocketConnection.initialized` (engine side). On reconnect the engine resends `WSEventSourceConfig` before the next poll for each previously-initialized stream. So your Java provider must be idempotent on receiving config for the same `streamId`, not assume "first config = create / second = update".

9. **`AuthTokens` propagation is currently sent for outbound (engine→SDK) connections via `isOutbound: true` flag.** Comment in [`workflow-engine/pkg/kldplugins/auth_provider.go`](workflow-engine/pkg/kldplugins/auth_provider.go) admits this is migration compat. Treat tokens received via the in-band `authTokens` field as untrusted unless you've verified the engine is internal (mTLS).

10. **Each request has a `nanoid` ID, you must echo it back.** The engine's `roundTrip` matches on `envelope.id`. Don't generate a new one on the response. `asyncHandleWithResponse` does `resEnv.ID = txn.Envelope().ID` — replicate that pattern.

11. **Throttling is per-connection token-bucket on the SDK send side**, configured by `requestsPerSecond` + `burst`. If you implement throttling in Java, do it at the same boundary or you'll surprise the engine with bursts.

12. **`handle_transactions` returns a slice the SAME LENGTH as the input.** If your Java handler returns a different-sized array, the engine wraps each missing slot with `MsgWSNilResult`. If your handler errors entire-batch, return the error on the envelope, not nil results.

13. **The engine sends `WSEngineAPISubmitTransactions` *down* the same WS** — providers can submit transactions back to the engine using the same socket. If your Java handler needs that capability, you need a request/response channel back over the WS keyed by `id`, similar to `inflightRequests` in `handler_runtime.go`. If you don't need it, just ignore the message type but don't error on it.

14. **Inbound runtime cannot "wait stopped" the way outbound can**, and `Stop` on the server wrapper closes the http.Server but doesn't actively kick connected clients beyond what the listener shutdown does. Engine handles the disconnect; don't try to force-cleanup individual sockets from the SDK side.

15. **Dual-mode validation in `BuildHandlerRuntimeConfig`** ([config.go](workflow-engine/pkg/enginesdk/config.go) lines 99-128): if `server.address` is set, server mode wins; else outbound mode. If both `server` and ws config are present, `NewHandlerRuntime` returns `MsgSDKConfigMutuallyExclusive`. Match this exactly to keep behaviour consistent across SDKs.

If you want, I can capture this as a `.cursor/plans/*.md` reference doc next to the existing BAF plan so your Java side has it as a stable artifact — say the word and I'll write it.