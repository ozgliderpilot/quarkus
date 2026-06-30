package io.quarkus.flyway.mongodb.test;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.database.nc.mongodb.MongoDBConfigurationExtension;

import io.quarkus.flyway.mongodb.FlywayMongodbConfigurationCustomizer;

/**
 * Routes Flyway's {@code mongosh} invocation through {@code docker exec} into the shared
 * {@code mongo:7} test container (see {@link MongoContainerExtension}), so {@code .js} migrations use
 * the container's {@code mongosh} instead of a host-installed one. Relies on the {@code shellCommand}
 * option from <a href="https://github.com/flyway/flyway/issues/4242">flyway/flyway#4242</a>.
 */
@ApplicationScoped
public class ContainerShellCommandCustomizer implements FlywayMongodbConfigurationCustomizer {

    @Override
    public void customize(FluentConfiguration configuration) {
        String containerId = System.getProperty(MongoContainerExtension.CONTAINER_ID_PROPERTY);
        if (containerId == null || containerId.isBlank()) {
            throw new IllegalStateException("MongoDB test container id is not set; "
                    + "is MongoContainerExtension registered on the test class?");
        }
        MongoDBConfigurationExtension mongoConfig = configuration.getPluginRegister()
                .getExact(MongoDBConfigurationExtension.class);
        if (mongoConfig == null) {
            throw new IllegalStateException(
                    "MongoDBConfigurationExtension is not registered; the patched flyway-database-nc-mongodb "
                            + "(flyway/flyway#4242) is required for container-routed .js migrations.");
        }
        mongoConfig.setShellCommand(List.of("docker", "exec", "-i", containerId, "mongosh"));
    }
}
