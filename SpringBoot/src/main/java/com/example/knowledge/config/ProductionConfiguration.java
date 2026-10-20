package com.example.knowledge.config;

import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class ProductionConfiguration {
    public ProductionConfiguration(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) return;
        if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException("prod cannot be combined with dev or test");
        }
        String secret = environment.getProperty("knowledge.jwt-secret", "");
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || secret.startsWith("change-this-")
                || secret.startsWith("test-secret")) {
            throw new IllegalStateException("Production requires an independent JWT_SECRET of at least 32 bytes");
        }
        String password = environment.getProperty("spring.datasource.password", "");
        if (password.length() < 12 || Set.of("knowledge", "root", "password", "123456").contains(password)
                || password.startsWith("change-this-")) {
            throw new IllegalStateException("Production requires an independent database password of at least 12 characters");
        }
        if (environment.getProperty("spring.datasource.username", "").isBlank()
                || "root".equalsIgnoreCase(environment.getProperty("spring.datasource.username"))) {
            throw new IllegalStateException("Production requires a dedicated non-root database account");
        }
    }
}
