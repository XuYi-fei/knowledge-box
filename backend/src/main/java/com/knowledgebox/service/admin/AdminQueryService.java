package com.knowledgebox.service.admin;

import com.knowledgebox.api.AdminDashboardView;
import com.knowledgebox.api.AgentProfileVersionView;
import com.knowledgebox.api.AgentTraceView;
import com.knowledgebox.api.IngestionJobView;
import com.knowledgebox.api.KnowledgeDocumentView;
import com.knowledgebox.api.McpServerView;
import com.knowledgebox.api.ModelCatalogView;
import com.knowledgebox.api.SkillBindingView;
import com.knowledgebox.api.ToolDefinitionView;
import com.knowledgebox.api.WebhookSubscriptionView;
import com.knowledgebox.domain.agent.AgentProfileVersion;
import com.knowledgebox.domain.agent.ModelCatalog;
import com.knowledgebox.domain.document.IngestionJobStatus;
import com.knowledgebox.domain.hook.HookEventType;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.AgentTraceRepository;
import com.knowledgebox.repository.KnowledgeDocumentRepository;
import com.knowledgebox.repository.ModelCatalogRepository;
import com.knowledgebox.service.document.DocumentGovernanceService;
import com.knowledgebox.service.integration.IntegrationAdminService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminQueryService {

    private final AgentProfileVersionRepository agentProfileVersionRepository;
    private final ModelCatalogRepository modelCatalogRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final DocumentGovernanceService documentGovernanceService;
    private final IntegrationAdminService integrationAdminService;

    public AdminQueryService(
            AgentProfileVersionRepository agentProfileVersionRepository,
            ModelCatalogRepository modelCatalogRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            AgentTraceRepository agentTraceRepository,
            DocumentGovernanceService documentGovernanceService,
            IntegrationAdminService integrationAdminService
    ) {
        this.agentProfileVersionRepository = agentProfileVersionRepository;
        this.modelCatalogRepository = modelCatalogRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.documentGovernanceService = documentGovernanceService;
        this.integrationAdminService = integrationAdminService;
    }

    public AdminDashboardView dashboard() {
        return new AdminDashboardView(
                Math.toIntExact(agentProfileVersionRepository.count()),
                Math.toIntExact(knowledgeDocumentRepository.count()),
                1,
                Math.toIntExact(agentTraceRepository.count())
        );
    }

    public List<AgentProfileVersionView> profileVersions() {
        return agentProfileVersionRepository.findAllForAdmin().stream()
                .map(this::toProfileVersionView)
                .toList();
    }

    public List<ModelCatalogView> modelCatalogs() {
        return modelCatalogRepository.findAllByOrderByModelTypeAscDisplayNameAsc().stream()
                .map(this::toModelCatalogView)
                .toList();
    }

    public List<KnowledgeDocumentView> documents() {
        return documentGovernanceService.documents();
    }

    public List<IngestionJobView> ingestionJobs() {
        return List.of(
                new IngestionJobView(1L, 1L, "系统设计说明", IngestionJobStatus.COMPLETED, "MARKDOWN_INGESTION", "{\"chunks\": 12}"),
                new IngestionJobView(2L, 2L, "上传规范", IngestionJobStatus.RUNNING, "MARKDOWN_INGESTION", "{\"stage\": \"rewrite-assets\"}")
        );
    }

    public List<ToolDefinitionView> tools() {
        return integrationAdminService.tools();
    }

    public List<McpServerView> mcpServers() {
        return integrationAdminService.mcpServers();
    }

    public List<SkillBindingView> skills() {
        return integrationAdminService.skills();
    }

    public List<WebhookSubscriptionView> hooks() {
        return List.of(
                new WebhookSubscriptionView(1L, HookEventType.ANSWER_COMPLETED, "https://example.com/hooks/answer", "kb_****_signed", true)
        );
    }

    public List<AgentTraceView> traces() {
        return List.of(
                new AgentTraceView(1L, "trace-1001", "session-001", "SUPERVISOR_THOUGHT", "{\"reasoning\": \"query needs retrieval\"}"),
                new AgentTraceView(2L, "trace-1002", "session-001", "RETRIEVAL_FINISHED", "{\"hits\": 2}")
        );
    }

    private AgentProfileVersionView toProfileVersionView(AgentProfileVersion version) {
        return new AgentProfileVersionView(
                version.getId(),
                version.getProfile().getCode(),
                version.getVersionNumber(),
                version.getStatus(),
                Boolean.TRUE.equals(version.getPublished()),
                version.getChatModel(),
                version.getRoutingModel(),
                version.getEmbeddingModel(),
                version.getRerankModel(),
                version.getTemperature(),
                version.getRetrievalTopK(),
                version.getReasoningBudget()
        );
    }

    private ModelCatalogView toModelCatalogView(ModelCatalog modelCatalog) {
        return new ModelCatalogView(
                modelCatalog.getId(),
                modelCatalog.getCode(),
                modelCatalog.getDisplayName(),
                modelCatalog.getProvider(),
                modelCatalog.getModelType(),
                modelCatalog.getDescription(),
                Boolean.TRUE.equals(modelCatalog.getEnabled()),
                Boolean.TRUE.equals(modelCatalog.getPublicSelectable()),
                Boolean.TRUE.equals(modelCatalog.getDefaultForPublic())
        );
    }
}
