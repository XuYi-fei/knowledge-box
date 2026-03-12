package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.ToolDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolDefinitionRepository extends JpaRepository<ToolDefinition, Long> {

    Optional<ToolDefinition> findByCode(String code);

    boolean existsByCode(String code);
}
