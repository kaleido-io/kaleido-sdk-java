// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** An event in an activity. */
public record ActivityEvent(String id, String name, String displayName, String description, JsonNode info, Parent parent, String topic, Long sequence, String activity, String asset, Map<String, String> labels, String created, String updated) {
}
