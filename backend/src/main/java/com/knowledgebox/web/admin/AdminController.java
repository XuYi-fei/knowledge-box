package com.knowledgebox.web.admin;

import com.knowledgebox.api.AdminDashboardView;
import com.knowledgebox.api.AgentProfileVersionView;
import com.knowledgebox.api.AgentTraceView;
import com.knowledgebox.api.ChangeAdminPasswordRequest;
import com.knowledgebox.api.CreateModelCatalogRequest;
import com.knowledgebox.api.IngestionJobView;
import com.knowledgebox.api.KnowledgeDocumentView;
import com.knowledgebox.api.McpServerView;
import com.knowledgebox.api.ModelCatalogView;
import com.knowledgebox.api.SkillBindingView;
import com.knowledgebox.api.ToolDefinitionView;
import com.knowledgebox.api.UpdateAgentProfileVersionRequest;
import com.knowledgebox.api.UpdateModelCatalogRequest;
import com.knowledgebox.api.WebhookSubscriptionView;
import com.knowledgebox.service.admin.AdminCommandService;
import com.knowledgebox.service.admin.AdminQueryService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminQueryService adminQueryService;
    private final AdminCommandService adminCommandService;

    public AdminController(AdminQueryService adminQueryService, AdminCommandService adminCommandService) {
        this.adminQueryService = adminQueryService;
        this.adminCommandService = adminCommandService;
    }

    @GetMapping("/me")
    public Map<String, String> me(Principal principal) {
        return Map.of("username", principal.getName(), "role", "ADMIN");
    }

    @PostMapping("/me/password")
    public Map<String, String> changePassword(
            Principal principal,
            @Valid @RequestBody ChangeAdminPasswordRequest request
    ) {
        adminCommandService.changeAdminPassword(
                principal.getName(),
                request.currentPassword(),
                request.newPassword()
        );
        return Map.of("message", "管理员密码修改成功");
    }

    @GetMapping("/dashboard")
    public AdminDashboardView dashboard() {
        return adminQueryService.dashboard();
    }

    @GetMapping("/profile-versions")
    public List<AgentProfileVersionView> profileVersions() {
        return adminQueryService.profileVersions();
    }

    @PutMapping("/profile-versions/{id}")
    public AgentProfileVersionView updateProfileVersion(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAgentProfileVersionRequest request
    ) {
        return adminCommandService.updateProfileVersion(id, request);
    }

    @GetMapping("/model-catalogs")
    public List<ModelCatalogView> modelCatalogs() {
        return adminQueryService.modelCatalogs();
    }

    @PostMapping("/model-catalogs")
    public ModelCatalogView createModelCatalog(@Valid @RequestBody CreateModelCatalogRequest request) {
        return adminCommandService.createModelCatalog(request);
    }

    @PutMapping("/model-catalogs/{id}")
    public ModelCatalogView updateModelCatalog(
            @PathVariable Long id,
            @Valid @RequestBody UpdateModelCatalogRequest request
    ) {
        return adminCommandService.updateModelCatalog(id, request);
    }

    @GetMapping("/documents")
    public List<KnowledgeDocumentView> documents() {
        return adminQueryService.documents();
    }

    @GetMapping("/ingestion-jobs")
    public List<IngestionJobView> ingestionJobs() {
        return adminQueryService.ingestionJobs();
    }

    @GetMapping("/tools")
    public List<ToolDefinitionView> tools() {
        return adminQueryService.tools();
    }

    @GetMapping("/mcp-servers")
    public List<McpServerView> mcpServers() {
        return adminQueryService.mcpServers();
    }

    @GetMapping("/skills")
    public List<SkillBindingView> skills() {
        return adminQueryService.skills();
    }

    @GetMapping("/hooks")
    public List<WebhookSubscriptionView> hooks() {
        return adminQueryService.hooks();
    }

    @GetMapping("/traces")
    public List<AgentTraceView> traces() {
        return adminQueryService.traces();
    }
}
