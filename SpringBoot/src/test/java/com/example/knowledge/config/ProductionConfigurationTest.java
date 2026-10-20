package com.example.knowledge.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ProductionConfiguration.class)
            .withPropertyValues("spring.profiles.active=prod", "knowledge.jwt-secret=7fa817ac37b140cdb139a85768b2e8088d391a49eb4a4d479bc942fd916167fe",
                    "spring.datasource.username=knowledge", "spring.datasource.password=unique-database-password");

    @Test void acceptsExplicitProductionCredentials() {
        context.run(c -> assertThat(c).hasNotFailed());
    }
    @Test void rejectsPublicJwtSecret() {
        context.withPropertyValues("knowledge.jwt-secret=change-this-development-secret-to-at-least-32-chars")
                .run(c -> assertThat(c).hasFailed());
    }
    @Test void rejectsMissingJwtSecret() {
        context.withPropertyValues("knowledge.jwt-secret=").run(c -> assertThat(c).hasFailed());
    }
    @Test void rejectsDefaultDatabasePassword() {
        context.withPropertyValues("spring.datasource.password=knowledge").run(c -> assertThat(c).hasFailed());
    }
    @Test void rejectsRootDatabaseAccount() {
        context.withPropertyValues("spring.datasource.username=root").run(c -> assertThat(c).hasFailed());
    }
    @Test void rejectsMixedDevelopmentAndProductionProfiles() {
        context.withPropertyValues("spring.profiles.active=prod,dev").run(c -> assertThat(c).hasFailed());
    }
    @Test void preservesLocalDevelopmentDefaults() {
        context.withPropertyValues("spring.profiles.active=dev", "knowledge.jwt-secret=", "spring.datasource.password=knowledge")
                .run(c -> assertThat(c).hasNotFailed());
    }
}
