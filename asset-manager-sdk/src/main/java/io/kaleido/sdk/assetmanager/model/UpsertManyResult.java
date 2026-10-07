// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

import java.util.List;

/** What a bulk upsert did to the objects of one type. */
public record UpsertManyResult(List<NameAndId> created, List<NameAndId> replaced, List<NameAndId> updated,
        List<NameAndId> ignored) {

    /** How many objects the upsert touched, in any way. */
    public int size() {
        return count(created) + count(replaced) + count(updated) + count(ignored);
    }

    private static int count(List<?> items) {
        return items == null ? 0 : items.size();
    }
}
