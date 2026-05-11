// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.handlers;

import io.kaleido.wfe.sdk.protocol.WSEventProcessorBatchRequest;
import io.kaleido.wfe.sdk.protocol.WSEventProcessorBatchResult;

public interface EventProcessor extends Handler {
    WSEventProcessorBatchResult processEvents(WSEventProcessorBatchRequest request) throws Exception;
}
