// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import java.util.List;

/**
 * Callback invoked when the provider-proxy dispatches a setup trigger. Runs
 * the registered setup hooks with the supplied authRef and returns the errors
 * from any failed hooks — an empty list means success.
 */
@FunctionalInterface
public interface SetupTriggerHandler {
    List<String> runSetup(String authRef) throws Exception;
}
