package com.knowledgebox.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ConfigBundleExportView(
        String schemaVersion,
        OffsetDateTime exportedAt,
        List<ConfigBundleToolView> tools,
        List<ConfigBundleMcpServerView> mcpServers,
        List<ConfigBundleSkillView> skills,
        List<AgentConfigSnapshotView> agents
) {
}
