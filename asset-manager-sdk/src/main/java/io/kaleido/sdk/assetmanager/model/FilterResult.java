// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * The items one bulk query matched for one type.
 *
 * @param count    how many items this result holds
 * @param total    how many items match, when asked for
 * @param allItems whether these are all the matching items
 * @param context  how the query was run
 * @param items    the items
 * @param <T>      the item type
 */
public record FilterResult<T>(Long count, Long total, boolean allItems, JsonNode context, List<T> items) {
}
