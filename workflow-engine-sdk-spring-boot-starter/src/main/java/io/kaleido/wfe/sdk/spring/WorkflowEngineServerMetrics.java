// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.spring;

import io.kaleido.wfe.sdk.server.WFEWebSocketServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Micrometer metrics auto-configuration for WFE SDK server mode.
 *
 * <p>Registers meters when Micrometer is on the classpath:
 * <ul>
 *   <li>{@code kaleido.wfe.server.running} (gauge) -- 1 when server is running</li>
 * </ul>
 */
@AutoConfiguration(after = WorkflowEngineAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(WFEWebSocketServer.class)
public class WorkflowEngineServerMetrics {

    @Bean
    public MeterBinder kaleidoWfeServerMeterBinder(WFEWebSocketServer server) {
        return registry -> registry.gauge("kaleido.wfe.server.running", server,
                s -> s.isRunning() ? 1.0 : 0.0);
    }
}
