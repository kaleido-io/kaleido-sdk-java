// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Links to FireFly objects; each data-model type uses some of these. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FireFlyLinks(String namespace, String api, String data, String blockchainEvent) {
}
