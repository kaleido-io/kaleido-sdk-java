// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** An object a bulk upsert touched. */
public record NameAndId(String name, String id, String parent) {
}
