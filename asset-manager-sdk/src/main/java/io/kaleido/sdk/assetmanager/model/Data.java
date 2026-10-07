// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** A data item. */
public record Data(String id, String name, String displayName, String description, JsonNode info, String uri, String transactionHash, String role, FireFlyLinks firefly, Parent parent, String asset, Map<String, String> labels, String created, String updated) {
}
