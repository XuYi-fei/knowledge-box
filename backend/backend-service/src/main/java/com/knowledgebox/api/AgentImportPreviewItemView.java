package com.knowledgebox.api;

import java.util.List;

public record AgentImportPreviewItemView(
        String profileCode,
        AgentImportItemStatus status,
        List<AgentImportResolutionAction> availableActions,
        AgentImportResolutionAction defaultAction,
        List<String> messages,
        AgentConfigSnapshotView incoming,
        AgentConfigSnapshotView existing
) {
}
