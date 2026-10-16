// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** An asset. */
public record Asset(String id, String name, String displayName, String description, JsonNode info, String collection, Map<String, String> labels, String created, String updated) {
}
