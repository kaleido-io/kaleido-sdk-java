// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** An address's balance of an asset or pool. */
public record Balance(String id, String address, String asset, String pool, String balanceAfter, String updated) {
}
