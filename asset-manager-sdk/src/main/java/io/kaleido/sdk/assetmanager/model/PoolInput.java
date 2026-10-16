// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** A pool to create, update or upsert. */
public final class PoolInput extends NamedInput<PoolInput> {

    private String standard;
    private FireFlyLinks firefly;
    private String asset;
    private String address;

    public PoolInput standard(String standard) {
        this.standard = standard;
        return this;
    }

    public PoolInput firefly(FireFlyLinks firefly) {
        this.firefly = firefly;
        return this;
    }

    /** The asset the pool belongs to, by name or id. */
    public PoolInput asset(String asset) {
        this.asset = asset;
        return this;
    }

    /** The contract address that scopes the pool. */
    public PoolInput address(String address) {
        this.address = address;
        return this;
    }
}
