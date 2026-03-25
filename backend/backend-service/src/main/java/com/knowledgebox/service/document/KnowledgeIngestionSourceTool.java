package com.knowledgebox.service.document;

import com.knowledgebox.domain.document.KnowledgeIngestionDraft;
import com.knowledgebox.domain.document.KnowledgeIngestionDraftSourceType;
import com.knowledgebox.repository.KnowledgeIngestionDraftRepository;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIngestionSourceTool {

    private final KnowledgeIngestionDraftRepository draftRepository;

    public KnowledgeIngestionSourceTool(KnowledgeIngestionDraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    @Tool(
            name = "readMarkdownSource",
            description = "Load the raw Markdown source of an ingestion draft. Use only when the draft source type is MARKDOWN."
    )
    public String readMarkdownSource(
            @ToolParam(name = "draftId", description = "The numeric draft ID to read.") Long draftId
    ) {
        KnowledgeIngestionDraft draft = loadDraft(draftId);
        if (draft.getSourceType() != KnowledgeIngestionDraftSourceType.MARKDOWN) {
            throw new IllegalArgumentException("Draft " + draftId + " is not a MARKDOWN source.");
        }
        return draft.getSourceContent();
    }

    @Tool(
            name = "readPdfSource",
            description = "Load the extracted text content of a PDF ingestion draft. Use only when the draft source type is PDF."
    )
    public String readPdfSource(
            @ToolParam(name = "draftId", description = "The numeric draft ID to read.") Long draftId
    ) {
        KnowledgeIngestionDraft draft = loadDraft(draftId);
        if (draft.getSourceType() != KnowledgeIngestionDraftSourceType.PDF) {
            throw new IllegalArgumentException("Draft " + draftId + " is not a PDF source.");
        }
        return draft.getSourceContent();
    }

    private KnowledgeIngestionDraft loadDraft(Long draftId) {
        if (draftId == null) {
            throw new IllegalArgumentException("draftId is required");
        }
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));
    }
}
