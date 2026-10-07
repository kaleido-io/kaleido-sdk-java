// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** A balance change recorded for a transfer. */
public record BalanceChange(String id, String name, String address, String operation, String amount, String asset, Parent parent, String transfer, String balanceBefore, String balanceAfter, String created, String updated) {
}
