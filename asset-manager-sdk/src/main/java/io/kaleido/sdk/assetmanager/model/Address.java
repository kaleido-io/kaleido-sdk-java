// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** An address. */
public record Address(String address, String displayName, String description, JsonNode info, Boolean contract, ContractManager contractManager, FireFlyLinks firefly, Map<String, String> labels, String created, String updated) {
}
