// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.assetmanager.model;

/** An activity event to create, update or upsert. */
public final class EventInput extends NamedInput<EventInput> {

    private Parent parent;
    private String activity;

    /** The object the event is about. */
    public EventInput parent(Parent parent) {
        this.parent = parent;
        return this;
    }

    /** The activity the event belongs to, by name or id. */
    public EventInput activity(String activity) {
        this.activity = activity;
        return this;
    }
}
