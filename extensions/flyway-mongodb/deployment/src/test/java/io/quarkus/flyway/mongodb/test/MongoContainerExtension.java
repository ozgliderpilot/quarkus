package io.quarkus.flyway.mongodb.test;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a single shared {@code mongo:7} container for the Flyway-MongoDB tests, replacing the
 * embedded-Flapdoodle harness. The image ships {@code mongosh}, so {@code .js} migrations run via
 * {@code docker exec <container> mongosh} (see {@link ContainerShellCommandCustomizer}) without a
 * host-installed {@code mongosh}. {@code mongod} listens on {@link #MONGO_PORT} both inside the
 * container and on the host, so the host and container views of the connection URL coincide.
 */
class MongoContainerExtension implements BeforeAllCallback {

    static final int MONGO_PORT = 27018;
    static final String MONGO_CONNECTION_STRING = "mongodb://127.0.0.1:" + MONGO_PORT;

    /** System property carrying the running container id for {@link ContainerShellCommandCustomizer}. */
    static final String CONTAINER_ID_PROPERTY = "quarkus.flyway-mongodb.test.container-id";

    private static final Logger LOGGER = Logger.getLogger(MongoContainerExtension.class);
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
            .create(MongoContainerExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        ExtensionContext.Store store = context.getRoot().getStore(NAMESPACE);
        if (store.get(MongoHandle.class) == null) {
            store.put(MongoHandle.class, new MongoHandle());
        }
    }

    private static final class MongoHandle implements AutoCloseable {

        private final GenericContainer<?> container;

        MongoHandle() {
            LOGGER.infof("Starting mongo:7 container with mongod on fixed port %d", MONGO_PORT);
            container = new FixedPortMongoContainer();
            container.start();
            System.setProperty(CONTAINER_ID_PROPERTY, container.getContainerId());
        }

        @Override
        public void close() {
            LOGGER.info("Stopping mongo:7 container");
            System.clearProperty(CONTAINER_ID_PROPERTY);
            container.stop();
        }
    }

    private static final class FixedPortMongoContainer extends GenericContainer<FixedPortMongoContainer> {

        FixedPortMongoContainer() {
            super(DockerImageName.parse("mongo:7"));
            addFixedExposedPort(MONGO_PORT, MONGO_PORT);
            // --bind_ip_all: overriding the command drops the image default that binds all interfaces,
            // which would otherwise leave mongod on the container loopback, unreachable via the host port.
            withCommand("--port", String.valueOf(MONGO_PORT), "--bind_ip_all");
            // mongosh runs inside the container, so the host temp file Flyway passes via `--file` must
            // resolve there too. The Quarkus build pins java.io.tmpdir to the module's target dir.
            String tempDir = System.getProperty("java.io.tmpdir");
            withFileSystemBind(tempDir, tempDir, BindMode.READ_ONLY);
            waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1));
        }
    }
}
