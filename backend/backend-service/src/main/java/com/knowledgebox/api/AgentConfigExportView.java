package com.knowledgebox.api;

import java.time.OffsetDateTime;
import java.util.List;

public record AgentConfigExportView(
        String schemaVersion,
        OffsetDateTime exportedAt,
        List<AgentConfigSnapshotView> agents
) {
}
