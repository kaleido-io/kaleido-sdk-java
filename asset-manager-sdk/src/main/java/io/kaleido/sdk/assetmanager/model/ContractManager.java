// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The contract manager build behind a contract address. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractManager(String service, String build) {
}
