// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** An address to create, update or upsert. */
public final class AddressInput extends DataModelInput<AddressInput> {

    private String address;
    private Boolean contract;
    private ContractManager contractManager;
    private FireFlyLinks firefly;

    public AddressInput address(String address) {
        this.address = address;
        return this;
    }

    /** Whether the address is a contract. */
    public AddressInput contract(Boolean contract) {
        this.contract = contract;
        return this;
    }

    public AddressInput contractManager(ContractManager contractManager) {
        this.contractManager = contractManager;
        return this;
    }

    public AddressInput firefly(FireFlyLinks firefly) {
        this.firefly = firefly;
        return this;
    }
}
