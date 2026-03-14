package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.McpServerConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpServerConfigRepository extends JpaRepository<McpServerConfig, Long> {

    Optional<McpServerConfig> findByCode(String code);

    boolean existsByCode(String code);
}
