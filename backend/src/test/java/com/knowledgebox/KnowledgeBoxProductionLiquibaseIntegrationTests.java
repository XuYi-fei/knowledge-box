package com.knowledgebox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = PostgresIntegrationTestSupport.ProductionInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KnowledgeBoxProductionLiquibaseIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @AfterAll
    void tearDown() {
        PostgresIntegrationTestSupport.dropSchema(
                environment.getRequiredProperty("kb.it.schema"),
                java.nio.file.Path.of(environment.getRequiredProperty("kb.it.upload-path"))
        );
    }

    @Test
    void shouldApplyProductionLiquibaseWithVectorColumn() {
        String currentSchema = jdbcTemplate.queryForObject("select current_schema()", String.class);
        String vectorColumnType = jdbcTemplate.queryForObject("""
                select udt_name
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'document_chunk'
                  and column_name = 'embedding'
                """, String.class);
        Integer extensionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_extension
                where extname = 'vector'
                """, Integer.class);
        Integer modelCatalogCount = jdbcTemplate.queryForObject("""
                select count(*)
                from model_catalog
                """, Integer.class);
        Integer publicChatModelCount = jdbcTemplate.queryForObject("""
                select count(*)
                from model_catalog
                where model_type = 'CHAT'
                  and public_selectable = true
                """, Integer.class);
        Integer publishedRoutingModelCount = jdbcTemplate.queryForObject("""
                select count(*)
                from agent_profile_version
                where published = true
                  and routing_model is not null
                  and trim(routing_model) <> ''
                """, Integer.class);

        assertThat(currentSchema).startsWith("kb_prod_it_");
        assertThat(vectorColumnType).isEqualTo("vector");
        assertThat(extensionCount).isGreaterThanOrEqualTo(1);
        assertThat(modelCatalogCount).isEqualTo(4);
        assertThat(publicChatModelCount).isEqualTo(2);
        assertThat(publishedRoutingModelCount).isGreaterThanOrEqualTo(1);
    }
}
