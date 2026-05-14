// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.spring;

import io.kaleido.wfe.sdk.client.WFEWebSocketClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Micrometer metrics auto-configuration for the Kaleido WFE SDK.
 *
 * <p>Registers meters when Micrometer is on the classpath:
 * <ul>
 *   <li>{@code kaleido.wfe.connected} (gauge) -- 1 when WebSocket is connected</li>
 * </ul>
 */
@AutoConfiguration(after = WorkflowEngineAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(WFEWebSocketClient.class)
public class WorkflowEngineMetrics {

    @Bean
    public MeterBinder kaleidoWfeMeterBinder(WFEWebSocketClient client) {
        return registry -> registry.gauge("kaleido.wfe.connected", client,
                c -> c.isConnected() ? 1.0 : 0.0);
    }
}
