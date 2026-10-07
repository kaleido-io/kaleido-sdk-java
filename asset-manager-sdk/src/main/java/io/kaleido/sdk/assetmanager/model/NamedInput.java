// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/**
 * A data-model input identified by name.
 *
 * @param <T> the input type, so each setter returns it
 */
public abstract class NamedInput<T extends NamedInput<T>> extends DataModelInput<T> {

    private String name;

    public T name(String name) {
        this.name = name;
        return self();
    }
}
