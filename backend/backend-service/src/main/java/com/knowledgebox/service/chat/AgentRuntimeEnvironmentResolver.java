package com.knowledgebox.service.chat;

import com.knowledgebox.domain.agent.AgentProfileVersionEnvVar;
import com.knowledgebox.domain.agent.AgentRuntimeEnvValueSource;
import com.knowledgebox.repository.AgentProfileVersionEnvVarRepository;
import com.knowledgebox.service.integration.IntegrationSecretCipherService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentRuntimeEnvironmentResolver {

    private final AgentProfileVersionEnvVarRepository envVarRepository;
    private final IntegrationSecretCipherService secretCipherService;

    public AgentRuntimeEnvironmentResolver(
            AgentProfileVersionEnvVarRepository envVarRepository,
            IntegrationSecretCipherService secretCipherService
    ) {
        this.envVarRepository = envVarRepository;
        this.secretCipherService = secretCipherService;
    }

    public List<AgentProfileVersionEnvVar> listByProfileVersionId(Long profileVersionId) {
        if (profileVersionId == null) {
            return List.of();
        }
        return envVarRepository.findByProfileVersionIdOrderByEnvKeyAsc(profileVersionId);
    }

    public AgentRuntimeEnvironment resolve(Long profileVersionId) {
        List<AgentProfileVersionEnvVar> envVars = listByProfileVersionId(profileVersionId);
        if (envVars.isEmpty()) {
            return AgentRuntimeEnvironment.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (AgentProfileVersionEnvVar envVar : envVars) {
            String value = resolveValue(envVar);
            if (value == null) {
                continue;
            }
            values.put(envVar.getEnvKey(), value);
        }
        return values.isEmpty() ? AgentRuntimeEnvironment.empty() : new AgentRuntimeEnvironment(values);
    }

    public boolean hasValue(AgentProfileVersionEnvVar envVar) {
        return StringUtils.hasText(resolveValue(envVar));
    }

    public String resolveValue(AgentProfileVersionEnvVar envVar) {
        if (envVar == null) {
            return null;
        }
        AgentRuntimeEnvValueSource source = envVar.getValueSource() == null
                ? AgentRuntimeEnvValueSource.INLINE
                : envVar.getValueSource();
        if (source == AgentRuntimeEnvValueSource.PROCESS_ENV) {
            if (!StringUtils.hasText(envVar.getSourceRef())) {
                return null;
            }
            return System.getenv(envVar.getSourceRef().trim());
        }
        if (!StringUtils.hasText(envVar.getValueEncrypted())) {
            return null;
        }
        return secretCipherService.decrypt(envVar.getValueEncrypted());
    }
}
