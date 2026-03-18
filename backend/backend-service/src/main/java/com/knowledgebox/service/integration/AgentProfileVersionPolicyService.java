package com.knowledgebox.service.integration;

import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.AgentProfileVersionType;
import com.knowledgebox.repository.AgentProfileVersionAgentBindingRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AgentProfileVersionPolicyService {

    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final AgentProfileVersionAgentBindingRepository agentBindingRepository;

    public AgentProfileVersionPolicyService(
            AgentProfileVersionRepository agentProfileVersionRepository,
            AgentProfileVersionAgentBindingRepository agentBindingRepository
    ) {
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.agentBindingRepository = agentBindingRepository;
    }

    public AgentProfileVersion requireVersion(Long profileVersionId) {
        if (profileVersionId == null) {
            throw new IllegalArgumentException("profileVersionId is required");
        }
        return agentProfileVersionRepository.findById(profileVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Agent profile version not found: " + profileVersionId));
    }

    public AgentProfileVersionType normalizeType(AgentProfileVersionType agentType) {
        return agentType == null ? AgentProfileVersionType.ENTRY : agentType;
    }

    public void validateTypeTransition(AgentProfileVersion version, AgentProfileVersionType requestedType) {
        AgentProfileVersionType targetType = normalizeType(requestedType);
        if (targetType == AgentProfileVersionType.MAIN
                && agentProfileVersionRepository.existsByAgentTypeAndIdNot(AgentProfileVersionType.MAIN, version.getId())) {
            throw new IllegalArgumentException("Only one MAIN agent version is allowed");
        }
        if (Boolean.TRUE.equals(version.getPublished()) && targetType != AgentProfileVersionType.MAIN) {
            throw new IllegalArgumentException("Only MAIN agent versions can stay published as the public chat entry");
        }
        if (targetType == AgentProfileVersionType.ATOMIC
                && agentBindingRepository.existsByParentProfileVersionId(version.getId())) {
            throw new IllegalArgumentException("ATOMIC agent versions cannot keep bound child agents");
        }
        if (targetType != AgentProfileVersionType.ATOMIC
                && agentBindingRepository.existsByChildProfileVersionId(version.getId())) {
            throw new IllegalArgumentException("Agent versions referenced as child agents must stay ATOMIC");
        }
    }

    public List<AgentProfileVersion> normalizeAndValidateChildBindings(
            AgentProfileVersion parentVersion,
            List<Long> childAgentVersionIds
    ) {
        AgentProfileVersionType parentType = normalizeType(parentVersion.getAgentType());
        if (parentType == AgentProfileVersionType.ATOMIC) {
            if (childAgentVersionIds == null || childAgentVersionIds.isEmpty()) {
                return List.of();
            }
            throw new IllegalArgumentException("ATOMIC agent versions cannot bind child agents");
        }

        Set<Long> normalizedIds = new LinkedHashSet<>();
        if (childAgentVersionIds != null) {
            for (Long childAgentVersionId : childAgentVersionIds) {
                if (childAgentVersionId != null) {
                    normalizedIds.add(childAgentVersionId);
                }
            }
        }
        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        List<AgentProfileVersion> versions = agentProfileVersionRepository.findAllByIdIn(normalizedIds);
        List<AgentProfileVersion> ordered = new ArrayList<>();
        for (Long childId : normalizedIds) {
            AgentProfileVersion childVersion = versions.stream()
                    .filter(version -> Objects.equals(version.getId(), childId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Child agent version not found: " + childId));
            validateParentChildRelation(parentVersion, childVersion);
            ordered.add(childVersion);
        }
        return List.copyOf(ordered);
    }

    private void validateParentChildRelation(AgentProfileVersion parentVersion, AgentProfileVersion childVersion) {
        if (Objects.equals(parentVersion.getId(), childVersion.getId())) {
            throw new IllegalArgumentException("Agent version cannot bind itself as child agent");
        }
        AgentProfileVersionType parentType = normalizeType(parentVersion.getAgentType());
        AgentProfileVersionType childType = normalizeType(childVersion.getAgentType());
        if (parentType != AgentProfileVersionType.MAIN
                && parentType != AgentProfileVersionType.ENTRY
                && parentType != AgentProfileVersionType.ORCHESTRATOR) {
            throw new IllegalArgumentException("Only MAIN, ENTRY or ORCHESTRATOR agent versions can bind child agents");
        }
        if (childType != AgentProfileVersionType.ATOMIC) {
            throw new IllegalArgumentException(
                    "Child agent version must be ATOMIC: profile="
                            + childVersion.getProfile().getCode()
                            + ", version="
                            + childVersion.getVersionNumber()
            );
        }
    }
}
