package com.knowledgebox;

import com.knowledgebox.config.KnowledgeBoxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(KnowledgeBoxProperties.class)
@EnableScheduling
public class KnowledgeBoxApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeBoxApplication.class, args);
    }
}
