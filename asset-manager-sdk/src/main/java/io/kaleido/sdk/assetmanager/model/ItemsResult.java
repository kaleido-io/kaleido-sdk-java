// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import java.util.List;

/**
 * One page of a list.
 *
 * @param count how many items this page holds
 * @param total how many items match, when asked for
 * @param items the items
 * @param <T>   the item type
 */
public record ItemsResult<T>(Long count, Long total, List<T> items) {
}
