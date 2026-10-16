// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** A pool. */
public record Pool(String id, String name, String qualifiedName, String displayName, String description, JsonNode info, String standard, FireFlyLinks firefly, String asset, String address, Map<String, String> labels, String created, String updated) {
}
