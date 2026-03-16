package com.knowledgebox.repository;

import com.knowledgebox.domain.apptool.AppToolDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppToolDefinitionRepository extends JpaRepository<AppToolDefinition, Long> {

    Optional<AppToolDefinition> findByCode(String code);

    boolean existsByCode(String code);

    List<AppToolDefinition> findAllByOrderByDisplayOrderAscNameAscIdAsc();

    List<AppToolDefinition> findAllByEnabledTrueOrderByDisplayOrderAscNameAscIdAsc();
}
