// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.spring;

import io.kaleido.wfe.sdk.server.WFEWebSocketServer;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Health indicator auto-configuration for WFE SDK server mode.
 *
 * <p>Exposes a {@code kaleido-wfe-server} component under {@code /actuator/health}
 * when Spring Boot Actuator is on the classpath and the server bean is present.
 */
@AutoConfiguration(after = WorkflowEngineAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(WFEWebSocketServer.class)
public class WorkflowEngineServerHealthIndicator {

    @Bean
    public HealthIndicator kaleidoWfeServerHealthIndicator(WFEWebSocketServer server) {
        return () -> {
            if (server.isRunning()) {
                return Health.up()
                        .withDetail("mode", "server")
                        .withDetail("port", server.getPort())
                        .build();
            }
            return Health.down()
                    .withDetail("mode", "server")
                    .build();
        };
    }
}
