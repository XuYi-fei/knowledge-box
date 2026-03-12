package com.knowledgebox.repository;

import com.knowledgebox.domain.integration.AgentProfileVersionToolBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentProfileVersionToolBindingRepository extends JpaRepository<AgentProfileVersionToolBinding, Long> {

    List<AgentProfileVersionToolBinding> findByProfileVersionId(Long profileVersionId);

    long countByToolId(Long toolId);

    @Modifying
    @Query("delete from AgentProfileVersionToolBinding binding where binding.profileVersionId = :profileVersionId")
    int deleteByProfileVersionId(@Param("profileVersionId") Long profileVersionId);
}
