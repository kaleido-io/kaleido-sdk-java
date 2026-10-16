// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kaleido.sdk.core.JSON;
import io.kaleido.sdk.core.http.ServiceClientOptions;
import io.kaleido.sdk.core.http.ServiceProxy;
import io.kaleido.sdk.core.http.ServiceResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Records each request a client sends through the provider-proxy and answers it from a function. */
final class FakeAssetManager implements ServiceProxy {

    /** A request, its body's keys sorted so expectations do not depend on field order. */
    record Request(String method, String path, JsonNode body) {
        @Override
        public String toString() {
            return method + " " + path + (body == null ? "" : " " + sorted(body));
        }
    }

    static JsonNode sorted(JsonNode node) {
        if (node instanceof ObjectNode object) {
            var copy = JSON.MAPPER.createObjectNode();
            object.properties().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> copy.set(entry.getKey(), sorted(entry.getValue())));
            return copy;
        }
        if (node.isArray()) {
            var copy = JSON.MAPPER.createArrayNode();
            node.forEach(item -> copy.add(sorted(item)));
            return copy;
        }
        return node;
    }

    final List<Request> requests = new ArrayList<>();
    Function<Request, ServiceResponse> answer = request -> ok("{}");

    static ServiceResponse ok(String json) {
        return new ServiceResponse(200, Map.of(), json.getBytes(StandardCharsets.UTF_8));
    }

    static ServiceResponse status(int status, String body) {
        return new ServiceResponse(status, Map.of(), body.getBytes(StandardCharsets.UTF_8));
    }

    AssetManagerClient client() {
        return new AssetManagerClient(new ServiceClientOptions.WsProxy(this, "AssetManagerService", "s:am1", "ar-1"));
    }

    @Override
    public ServiceResponse send(String serviceType, String id, String authRef, String method, String path,
            Map<String, String> headers, Object body) {
        var request = new Request(method, path, body == null ? null : JSON.MAPPER.valueToTree(body));
        requests.add(request);
        return answer.apply(request);
    }

    List<String> sent() {
        return requests.stream().map(Request::toString).toList();
    }
}
