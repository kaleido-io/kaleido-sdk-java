// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import java.util.List;

/** A transfer to create, update or upsert, identified by its protocol id. */
public final class TransferInput extends DataModelInput<TransferInput> {

    private String protocolId;
    private String type;
    private String signer;
    private String from;
    private String to;
    private String amount;
    private FireFlyLinks firefly;
    private String transactionHash;
    private List<BalanceChangeInput> balanceChanges;
    private Parent parent;

    public TransferInput protocolId(String protocolId) {
        this.protocolId = protocolId;
        return this;
    }

    /** {@code mint}, {@code burn} or {@code transfer}. */
    public TransferInput type(String type) {
        this.type = type;
        return this;
    }

    public TransferInput signer(String signer) {
        this.signer = signer;
        return this;
    }

    public TransferInput from(String from) {
        this.from = from;
        return this;
    }

    public TransferInput to(String to) {
        this.to = to;
        return this;
    }

    public TransferInput amount(String amount) {
        this.amount = amount;
        return this;
    }

    public TransferInput firefly(FireFlyLinks firefly) {
        this.firefly = firefly;
        return this;
    }

    public TransferInput transactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
        return this;
    }

    public TransferInput balanceChanges(List<BalanceChangeInput> balanceChanges) {
        this.balanceChanges = balanceChanges;
        return this;
    }

    /** The pool or NFT the transfer belongs to. */
    public TransferInput parent(Parent parent) {
        this.parent = parent;
        return this;
    }
}
