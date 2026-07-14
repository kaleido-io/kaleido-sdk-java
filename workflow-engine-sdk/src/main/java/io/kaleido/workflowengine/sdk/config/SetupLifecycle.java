// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.config;

/**
 * Controls when handler setup() hooks run.
 *
 * <ul>
 *   <li>{@link #BOOT} (default): hooks run during start(), with no authRef in
 *       scope. Hosted-binding calls inside setup() will lack authentication and
 *       fail at the destination.</li>
 *   <li>{@link #DEFERRED}: hooks run only when the provider-proxy dispatches a
 *       SETUP_TRIGGER_REQUEST (service-manager initiates this during deploy).
 *       The trigger carries an authRef bound to the deploying user's JWT, so
 *       hooks can safely call hosted services.</li>
 * </ul>
 *
 * <p>The operator sets this field in the rendered KALEIDO_CONFIG_FILE on
 * platforms that ship the deploy-time setup-trigger machinery. Providers
 * running against older platforms will not see the field and default to
 * {@link #BOOT}.
 */
public enum SetupLifecycle {
    BOOT,
    DEFERRED;

    /** Config-file value for {@link #BOOT} — shared across the SDK family. */
    public static final String VALUE_BOOT = "boot";
    /** Config-file value for {@link #DEFERRED} — shared across the SDK family. */
    public static final String VALUE_DEFERRED = "deferred";
}
