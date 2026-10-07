// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** A fragment to create, update or upsert. */
public final class FragmentInput extends NamedInput<FragmentInput> {

    private String value;
    private Boolean valueMasked;
    private String valueReference;
    private String asset;
    private String address;

    public FragmentInput value(String value) {
        this.value = value;
        return this;
    }

    public FragmentInput valueMasked(Boolean valueMasked) {
        this.valueMasked = valueMasked;
        return this;
    }

    public FragmentInput valueReference(String valueReference) {
        this.valueReference = valueReference;
        return this;
    }

    /** The asset the fragment belongs to, by name or id. */
    public FragmentInput asset(String asset) {
        this.asset = asset;
        return this;
    }

    /** The address that scopes the fragment. */
    public FragmentInput address(String address) {
        this.address = address;
        return this;
    }
}
