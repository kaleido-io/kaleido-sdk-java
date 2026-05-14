// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.spring;

import io.kaleido.wfe.sdk.client.WFEWebSocketClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Health indicator auto-configuration for the Kaleido WFE SDK.
 *
 * <p>Exposes a {@code kaleido-wfe} component under {@code /actuator/health}
 * when Spring Boot Actuator is on the classpath.
 */
@AutoConfiguration(after = WorkflowEngineAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(WFEWebSocketClient.class)
public class WorkflowEngineHealthIndicator {

    @Bean
    public HealthIndicator kaleidoWfeHealthIndicator(WFEWebSocketClient client) {
        return () -> {
            if (client.isConnected()) {
                return Health.up()
                        .withDetail("websocket", "connected")
                        .build();
            }
            return Health.down()
                    .withDetail("websocket", "disconnected")
                    .build();
        };
    }
}
