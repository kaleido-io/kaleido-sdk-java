// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One address's balance change in a transfer.
 *
 * @param address   the address
 * @param operation {@code add} or {@code subtract}
 * @param amount    the amount, as a decimal string
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BalanceChangeInput(String address, String operation, String amount) {

    public static BalanceChangeInput add(String address, String amount) {
        return new BalanceChangeInput(address, "add", amount);
    }

    public static BalanceChangeInput subtract(String address, String amount) {
        return new BalanceChangeInput(address, "subtract", amount);
    }
}
