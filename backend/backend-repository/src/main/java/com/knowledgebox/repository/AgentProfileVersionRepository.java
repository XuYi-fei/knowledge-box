package com.knowledgebox.repository;

import com.knowledgebox.domain.agent.AgentProfileVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgentProfileVersionRepository extends JpaRepository<AgentProfileVersion, Long> {

    List<AgentProfileVersion> findByProfile_CodeOrderByVersionNumberDesc(String code);

    @EntityGraph(attributePaths = "profile")
    Optional<AgentProfileVersion> findFirstByPublishedTrueOrderByUpdatedAtDesc();

    @Query("select version from AgentProfileVersion version join fetch version.profile order by version.profile.code asc, version.versionNumber desc")
    List<AgentProfileVersion> findAllForAdmin();
}
