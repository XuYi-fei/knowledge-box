package com.knowledgebox.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIdType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnProperty(prefix = "knowledge-box.retrieval", name = "vector-enabled", havingValue = "true", matchIfMissing = true)
    VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, KnowledgeBoxProperties properties) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName(properties.getRetrieval().getVectorSchema())
                .dimensions(properties.getRetrieval().getEmbeddingDimensions())
                .vectorTableName(properties.getRetrieval().getVectorTable())
                .idType(PgIdType.TEXT)
                .initializeSchema(true)
                .build();
    }
}
