package com.knowledgebox.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ConfigBundleImportPreviewItemView(
        ConfigBundleResourceType resourceType,
        String resourceCode,
        String displayName,
        AgentImportItemStatus status,
        List<AgentImportResolutionAction> availableActions,
        AgentImportResolutionAction defaultAction,
        List<String> messages,
        JsonNode incoming,
        JsonNode existing
) {
}
