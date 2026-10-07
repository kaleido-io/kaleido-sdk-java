// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The object something belongs to.
 *
 * @param type the kind of object, e.g. {@code pool}, {@code nft}, {@code asset} or {@code address}
 * @param ref  the object, by name, id or qualified name
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Parent(String type, String ref) {

    public static Parent pool(String ref) {
        return new Parent("pool", ref);
    }

    public static Parent nft(String ref) {
        return new Parent("nft", ref);
    }
}
