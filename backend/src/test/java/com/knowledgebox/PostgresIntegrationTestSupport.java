package com.knowledgebox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

class PostgresIntegrationTestSupport {

    static final String DB_NAME = System.getProperty("kb.it.db.name", "postgres");
    static final String DB_USER = System.getProperty("kb.it.db.user",
            System.getenv().getOrDefault("KB_IT_DB_USER", System.getProperty("user.name")));
    static final String DB_PASSWORD = System.getProperty("kb.it.db.password",
            System.getenv().getOrDefault("KB_IT_DB_PASSWORD", ""));
    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/" + DB_NAME;

    private PostgresIntegrationTestSupport() {
    }

    static void createSchema(String schema) {
        executeAdminSql("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        executeAdminSql("CREATE SCHEMA " + schema);
    }

    static void dropSchema(String schema, Path uploadPath) {
        executeAdminSql("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        deleteUploadDir(uploadPath);
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private final String schema = schemaName("kb_it_");
        private final Path uploadPath = Path.of("backend/target/test-uploads", schema);
        private final String changeLog = "classpath:db/changelog/db.changelog-it.xml";

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            createSchema(schema);
            TestPropertyValues.of(
                    "spring.datasource.url=jdbc:postgresql://localhost:5432/" + DB_NAME + "?currentSchema=" + schema + ",public",
                    "spring.datasource.username=" + DB_USER,
                    "spring.datasource.password=" + DB_PASSWORD,
                    "spring.liquibase.change-log=" + changeLog,
                    "spring.liquibase.default-schema=" + schema,
                    "spring.jpa.properties.hibernate.default_schema=" + schema,
                    "spring.ai.dashscope.api-key=test-key",
                    "spring.ai.dashscope.agent.api-key=test-key",
                    "knowledge-box.admin.username=admin-it",
                    "knowledge-box.admin.password=admin-it-pass",
                    "knowledge-box.auth.jwt-secret=test-jwt-secret-for-integration-tests-2026",
                    "knowledge-box.chat.stub-responses=true",
                    "knowledge-box.retrieval.vector-enabled=false",
                    "knowledge-box.storage.local-base-path=" + uploadPath,
                    "knowledge-box.storage.public-base-url=http://localhost:8080/uploads/" + schema,
                    "kb.it.schema=" + schema,
                    "kb.it.upload-path=" + uploadPath
            ).applyTo(applicationContext.getEnvironment());
        }
    }

    static class ProductionInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private final String schema = schemaName("kb_prod_it_");
        private final Path uploadPath = Path.of("backend/target/test-uploads", schema);

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            createSchema(schema);
            TestPropertyValues.of(
                    "spring.datasource.url=jdbc:postgresql://localhost:5432/" + DB_NAME + "?currentSchema=" + schema + ",public",
                    "spring.datasource.username=" + DB_USER,
                    "spring.datasource.password=" + DB_PASSWORD,
                    "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml",
                    "spring.liquibase.default-schema=" + schema,
                    "spring.jpa.properties.hibernate.default_schema=" + schema,
                    "spring.ai.dashscope.api-key=test-key",
                    "spring.ai.dashscope.agent.api-key=test-key",
                    "knowledge-box.admin.username=admin-it",
                    "knowledge-box.admin.password=admin-it-pass",
                    "knowledge-box.auth.jwt-secret=test-jwt-secret-for-integration-tests-2026",
                    "knowledge-box.chat.stub-responses=true",
                    "knowledge-box.retrieval.vector-enabled=false",
                    "knowledge-box.storage.local-base-path=" + uploadPath,
                    "knowledge-box.storage.public-base-url=http://localhost:8080/uploads/" + schema,
                    "kb.it.schema=" + schema,
                    "kb.it.upload-path=" + uploadPath
            ).applyTo(applicationContext.getEnvironment());
        }
    }

    private static void executeAdminSql(String sql) {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to execute admin SQL for integration test: " + sql, exception);
        }
    }

    private static void deleteUploadDir(Path uploadPath) {
        if (!Files.exists(uploadPath)) {
            return;
        }
        try (var paths = Files.walk(uploadPath)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException("Failed to delete test upload path " + path, exception);
                        }
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to clean test uploads", exception);
        }
    }

    private static String schemaName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
