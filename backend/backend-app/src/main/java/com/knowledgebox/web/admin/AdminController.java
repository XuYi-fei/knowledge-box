package com.knowledgebox.web.admin;

import com.knowledgebox.api.AdminDashboardView;
import com.knowledgebox.api.AppToolDefinitionView;
import com.knowledgebox.api.AppToolExecutionLogPageView;
import com.knowledgebox.api.AgentExecutionTraceDetailView;
import com.knowledgebox.api.AgentExecutionTracePageView;
import com.knowledgebox.api.AgentProfileVersionView;
import com.knowledgebox.api.AgentProfileVersionBindingsView;
import com.knowledgebox.api.ChangeAdminPasswordRequest;
import com.knowledgebox.api.CreateAppToolDefinitionRequest;
import com.knowledgebox.api.CreateMcpServerRequest;
import com.knowledgebox.api.CreateModelCatalogRequest;
import com.knowledgebox.api.CreateToolDefinitionRequest;
import com.knowledgebox.api.IngestionJobView;
import com.knowledgebox.api.KnowledgeDocumentView;
import com.knowledgebox.api.McpServerView;
import com.knowledgebox.api.ModelCatalogView;
import com.knowledgebox.api.SkillBindingView;
import com.knowledgebox.api.ToolDefinitionView;
import com.knowledgebox.api.UpdateAppToolDefinitionRequest;
import com.knowledgebox.api.UpdateAgentProfileVersionBindingsRequest;
import com.knowledgebox.api.UpdateAgentProfileVersionRequest;
import com.knowledgebox.api.UpdateMcpServerRequest;
import com.knowledgebox.api.UpdateModelCatalogRequest;
import com.knowledgebox.api.UpdateSkillBindingRequest;
import com.knowledgebox.api.UpdateToolDefinitionRequest;
import com.knowledgebox.api.WebhookSubscriptionView;
import com.knowledgebox.service.admin.AdminCommandService;
import com.knowledgebox.service.admin.AgentExecutionTraceAdminService;
import com.knowledgebox.service.admin.AgentExecutionTraceQueryService;
import com.knowledgebox.service.admin.AdminQueryService;
import com.knowledgebox.service.apptool.AppToolAdminService;
import com.knowledgebox.service.integration.AgentProfileBindingService;
import com.knowledgebox.service.integration.IntegrationAdminService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminQueryService adminQueryService;
    private final AgentExecutionTraceQueryService agentExecutionTraceQueryService;
    private final AgentExecutionTraceAdminService agentExecutionTraceAdminService;
    private final AdminCommandService adminCommandService;
    private final IntegrationAdminService integrationAdminService;
    private final AgentProfileBindingService agentProfileBindingService;
    private final AppToolAdminService appToolAdminService;

    public AdminController(
            AdminQueryService adminQueryService,
            AgentExecutionTraceQueryService agentExecutionTraceQueryService,
            AgentExecutionTraceAdminService agentExecutionTraceAdminService,
            AdminCommandService adminCommandService,
            IntegrationAdminService integrationAdminService,
            AgentProfileBindingService agentProfileBindingService,
            AppToolAdminService appToolAdminService
    ) {
        this.adminQueryService = adminQueryService;
        this.agentExecutionTraceQueryService = agentExecutionTraceQueryService;
        this.agentExecutionTraceAdminService = agentExecutionTraceAdminService;
        this.adminCommandService = adminCommandService;
        this.integrationAdminService = integrationAdminService;
        this.agentProfileBindingService = agentProfileBindingService;
        this.appToolAdminService = appToolAdminService;
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

    @GetMapping("/profile-versions/{id}/bindings")
    public AgentProfileVersionBindingsView profileVersionBindings(@PathVariable Long id) {
        return agentProfileBindingService.bindings(id);
    }

    @PutMapping("/profile-versions/{id}/bindings")
    public AgentProfileVersionBindingsView updateProfileVersionBindings(
            @PathVariable Long id,
            @RequestBody UpdateAgentProfileVersionBindingsRequest request
    ) {
        return agentProfileBindingService.updateBindings(id, request);
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

    @PostMapping("/tools")
    public ToolDefinitionView createTool(@Valid @RequestBody CreateToolDefinitionRequest request) {
        return integrationAdminService.createTool(request);
    }

    @PutMapping("/tools/{code}")
    public ToolDefinitionView updateTool(
            @PathVariable String code,
            @Valid @RequestBody UpdateToolDefinitionRequest request
    ) {
        return integrationAdminService.updateTool(code, request);
    }

    @DeleteMapping("/tools/{code}")
    public Map<String, String> deleteTool(@PathVariable String code) {
        integrationAdminService.deleteTool(code);
        return Map.of("message", "Tool deleted");
    }

    @GetMapping("/app-tools")
    public List<AppToolDefinitionView> appTools() {
        return appToolAdminService.appTools();
    }

    @PostMapping("/app-tools")
    public AppToolDefinitionView createAppTool(@Valid @RequestBody CreateAppToolDefinitionRequest request) {
        return appToolAdminService.create(request);
    }

    @PutMapping("/app-tools/{code}")
    public AppToolDefinitionView updateAppTool(
            @PathVariable String code,
            @Valid @RequestBody UpdateAppToolDefinitionRequest request
    ) {
        return appToolAdminService.update(code, request);
    }

    @DeleteMapping("/app-tools/{code}")
    public Map<String, String> deleteAppTool(@PathVariable String code) {
        appToolAdminService.delete(code);
        return Map.of("message", "App tool deleted");
    }

    @GetMapping("/app-tool-executions")
    public AppToolExecutionLogPageView appToolExecutions(
            @RequestParam(required = false) String toolCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return appToolAdminService.executionLogs(toolCode, status, userId, page, pageSize);
    }

    @GetMapping("/mcp-servers")
    public List<McpServerView> mcpServers() {
        return adminQueryService.mcpServers();
    }

    @PostMapping("/mcp-servers")
    public McpServerView createMcpServer(@Valid @RequestBody CreateMcpServerRequest request) {
        return integrationAdminService.createMcpServer(request);
    }

    @PutMapping("/mcp-servers/{code}")
    public McpServerView updateMcpServer(
            @PathVariable String code,
            @Valid @RequestBody UpdateMcpServerRequest request
    ) {
        return integrationAdminService.updateMcpServer(code, request);
    }

    @DeleteMapping("/mcp-servers/{code}")
    public Map<String, String> deleteMcpServer(@PathVariable String code) {
        integrationAdminService.deleteMcpServer(code);
        return Map.of("message", "MCP server deleted");
    }

    @GetMapping("/skills")
    public List<SkillBindingView> skills() {
        return adminQueryService.skills();
    }

    @PostMapping(path = "/skills/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SkillBindingView uploadSkill(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) MultipartFile zip,
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) List<String> paths,
            @RequestParam(defaultValue = "false") boolean replace
    ) {
        return integrationAdminService.uploadSkill(code, name, description, enabled, zip, files, paths, replace);
    }

    @PutMapping("/skills/{code}")
    public SkillBindingView updateSkill(
            @PathVariable String code,
            @Valid @RequestBody UpdateSkillBindingRequest request
    ) {
        return integrationAdminService.updateSkill(code, request);
    }

    @DeleteMapping("/skills/{code}")
    public Map<String, String> deleteSkill(@PathVariable String code) {
        integrationAdminService.deleteSkill(code);
        return Map.of("message", "Skill deleted");
    }

    @GetMapping("/hooks")
    public List<WebhookSubscriptionView> hooks() {
        return adminQueryService.hooks();
    }

    @GetMapping("/traces")
    public AgentExecutionTracePageView traces(
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sessionCode,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String queryKeyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return agentExecutionTraceQueryService.traces(traceId, status, sessionCode, userId, queryKeyword, page, pageSize);
    }

    @GetMapping("/traces/{traceId}")
    public AgentExecutionTraceDetailView traceDetail(@PathVariable String traceId) {
        return agentExecutionTraceQueryService.traceDetail(traceId);
    }

    @DeleteMapping("/traces/{traceId}")
    public Map<String, String> deleteTrace(@PathVariable String traceId) {
        agentExecutionTraceAdminService.deleteTrace(traceId);
        return Map.of("message", "Trace deleted");
    }
}
