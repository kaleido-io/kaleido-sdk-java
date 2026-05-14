// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sample;

import io.kaleido.wfe.sdk.client.WFEWebSocketClient;
import io.kaleido.wfe.sdk.config.RuntimeConfig;
import io.kaleido.wfe.sdk.handlers.HandlerSetFor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class SampleProviderApp {

    public static void main(String[] args) {
        SpringApplication.run(SampleProviderApp.class, args);
    }

    private static RuntimeConfig resolveConfig() {
        // 1. WFE_CONFIG_FILE env var takes highest precedence
        var envPath = System.getenv("WFE_CONFIG_FILE");
        if (envPath != null && !envPath.isBlank()) {
            return RuntimeConfig.fromYaml(Path.of(envPath));
        }

        // 2. Classpath resource (standard Spring Boot convention)
        var classpathStream = SampleProviderApp.class.getClassLoader()
                .getResourceAsStream("wfe-config.yaml");
        if (classpathStream != null) {
            try (classpathStream) {
                return RuntimeConfig.fromYaml(classpathStream);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load classpath wfe-config.yaml", e);
            }
        }

        // 3. Filesystem fallback: config/wfe-config.yaml
        return RuntimeConfig.fromYaml(Path.of("config/wfe-config.yaml"));
    }

    @Bean
    CommandLineRunner wfeRunner() {
        return args -> {
            var config = resolveConfig();
            var client = new WFEWebSocketClient(config,
                    HandlerSetFor.of(new HelloTransactionHandler(), new EchoEventProcessor()));
            Runtime.getRuntime().addShutdownHook(new Thread(client::stop));
            client.connect().join();
            client.waitStopped();
        };
    }
}
