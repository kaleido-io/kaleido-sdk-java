// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sample;

import io.kaleido.workflowengine.sdk.client.WorkflowEngineClient;
import io.kaleido.workflowengine.sdk.config.ClientConfig;
import io.kaleido.workflowengine.sdk.config.ConfigLoader;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

@SpringBootApplication
public class SampleProviderApp {

    public static void main(String[] args) {
        SpringApplication.run(SampleProviderApp.class, args);
    }

    private static ClientConfig resolveConfig() {
        // 1. KALEIDO_CONFIG_FILE (or legacy WFE_CONFIG_FILE) env var takes highest precedence
        var envPath = ConfigLoader.resolveConfigPath(null);
        if (envPath != null) {
            return ConfigLoader.load(Path.of(envPath));
        }

        // 2. Classpath resource (standard Spring Boot convention)
        var classpathStream = SampleProviderApp.class.getClassLoader()
                .getResourceAsStream("kaleido-config.yaml");
        if (classpathStream != null) {
            try (classpathStream) {
                return ConfigLoader.load(classpathStream);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load classpath kaleido-config.yaml", e);
            }
        }

        // 3. Filesystem fallback
        return ConfigLoader.load(Path.of("config/kaleido-config.yaml"));
    }

    @Bean
    CommandLineRunner workflowEngineRunner() {
        return args -> {
            var client = new WorkflowEngineClient(resolveConfig());
            client.registerHandler(new HelloTransactionHandler());
            client.registerHandler(new EchoEventProcessor());
            Runtime.getRuntime().addShutdownHook(new Thread(client::stop));
            client.connect();
            client.waitStopped();
        };
    }
}
