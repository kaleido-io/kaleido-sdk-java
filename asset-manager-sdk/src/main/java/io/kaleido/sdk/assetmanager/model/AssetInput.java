// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** An asset to create, update or upsert. */
public final class AssetInput extends NamedInput<AssetInput> {

    private String collection;

    /** The collection the asset belongs to, by name or id. */
    public AssetInput collection(String collection) {
        this.collection = collection;
        return this;
    }
}
