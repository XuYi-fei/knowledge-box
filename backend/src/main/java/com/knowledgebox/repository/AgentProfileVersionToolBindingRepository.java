package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.AgentProfileVersionToolBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProfileVersionToolBindingRepository extends JpaRepository<AgentProfileVersionToolBinding, Long> {

    List<AgentProfileVersionToolBinding> findByProfileVersionId(Long profileVersionId);

    long countByToolCode(String toolCode);

    void deleteByProfileVersionId(Long profileVersionId);
}
