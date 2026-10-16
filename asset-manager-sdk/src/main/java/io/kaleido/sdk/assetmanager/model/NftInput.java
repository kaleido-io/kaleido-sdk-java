// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** An NFT to create, update or upsert. */
public final class NftInput extends NamedInput<NftInput> {

    private String standard;
    private String tokenIndex;
    private String uri;
    private Boolean active;
    private FireFlyLinks firefly;
    private String asset;
    private String address;

    public NftInput standard(String standard) {
        this.standard = standard;
        return this;
    }

    public NftInput tokenIndex(String tokenIndex) {
        this.tokenIndex = tokenIndex;
        return this;
    }

    public NftInput uri(String uri) {
        this.uri = uri;
        return this;
    }

    public NftInput active(Boolean active) {
        this.active = active;
        return this;
    }

    public NftInput firefly(FireFlyLinks firefly) {
        this.firefly = firefly;
        return this;
    }

    /** The asset the NFT belongs to, by name or id. */
    public NftInput asset(String asset) {
        this.asset = asset;
        return this;
    }

    /** The contract address that scopes the NFT. */
    public NftInput address(String address) {
        this.address = address;
        return this;
    }
}
