// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Setup trigger request: emitted by the provider-proxy when an admin dispatches
 * a deploy-time setup() via service-manager. The {@code authRef} references a
 * bearer the proxy has cached for the duration of the setup; the SDK passes it
 * through SetupContext so any service-proxy calls inside setup() are authorised
 * as the deploying user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSSetupTriggerRequest(
        WSMessageType messageType,
        String requestId,
        String authRef
) {}
