// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** A data item to create, update or upsert. */
public final class DataInput extends NamedInput<DataInput> {

    private String uri;
    private String transactionHash;
    private String role;
    private FireFlyLinks firefly;
    private Parent parent;

    public DataInput uri(String uri) {
        this.uri = uri;
        return this;
    }

    public DataInput transactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
        return this;
    }

    public DataInput role(String role) {
        this.role = role;
        return this;
    }

    public DataInput firefly(FireFlyLinks firefly) {
        this.firefly = firefly;
        return this;
    }

    /** The object the data belongs to. */
    public DataInput parent(Parent parent) {
        this.parent = parent;
        return this;
    }
}
