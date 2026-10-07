// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** A transfer. */
public record Transfer(String id, String protocolId, String displayName, String description, JsonNode info, String type, String signer, String from, String to, String amount, FireFlyLinks firefly, String transactionHash, List<BalanceChangeInput> balanceChanges, String asset, Parent parent, Map<String, String> labels, String created, String updated) {
}
