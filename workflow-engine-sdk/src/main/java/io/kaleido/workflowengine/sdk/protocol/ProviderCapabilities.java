// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-provider capability flags surfaced to the platform on registration.
 * Values are computed from the customer's actual handler registrations, not
 * fixed per SDK version — a provider built on the new SDK that doesn't define
 * any setup() hook correctly reports {@code hasSetupHooks: false}.
 *
 * <p>Additive: new flags can be introduced without a wire-format break —
 * receivers ignore unknown fields, and missing fields are treated as their
 * conservative default ({@code false} in every case defined so far).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderCapabilities(
        Boolean hasSetupHooks
) {}
