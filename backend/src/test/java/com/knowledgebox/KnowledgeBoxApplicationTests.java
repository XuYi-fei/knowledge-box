package com.knowledgebox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = PostgresIntegrationTestSupport.Initializer.class)
class KnowledgeBoxApplicationTests {

    @Autowired
    private ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @Test
    void contextLoads() {
        assertThat(embeddingModelProvider.getIfAvailable()).isNotNull();
    }
}
