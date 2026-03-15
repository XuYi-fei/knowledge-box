package com.knowledgebox;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledgebox.api.ChatResponse;
import com.knowledgebox.api.UserAuthResponse;
import com.knowledgebox.domain.chat.ChatMessageStatus;
import com.knowledgebox.domain.chat.ChatSession;
import com.knowledgebox.domain.chat.ChatSessionStatus;
import com.knowledgebox.domain.chat.ChatTurn;
import com.knowledgebox.domain.user.UserAccount;
import com.knowledgebox.repository.AgentProfileRepository;
import com.knowledgebox.repository.AgentProfileVersionRepository;
import com.knowledgebox.repository.ChatSessionRepository;
import com.knowledgebox.repository.ChatTurnRepository;
import com.knowledgebox.repository.IngestionJobRepository;
import com.knowledgebox.repository.KnowledgeDocumentRepository;
import com.knowledgebox.repository.UserAccountRepository;
import com.knowledgebox.security.JwtTokenService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = PostgresIntegrationTestSupport.Initializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KnowledgeBoxPostgresIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentProfileRepository agentProfileRepository;

    @Autowired
    private AgentProfileVersionRepository agentProfileVersionRepository;

    @Autowired
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatTurnRepository chatTurnRepository;

    @Autowired
    private IngestionJobRepository ingestionJobRepository;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @AfterAll
    void tearDown() {
        PostgresIntegrationTestSupport.dropSchema(
                environment.getRequiredProperty("kb.it.schema"),
                java.nio.file.Path.of(environment.getRequiredProperty("kb.it.upload-path"))
        );
    }

    @Test
    void shouldBootstrapLiquibaseIntoDedicatedSchema() {
        String currentSchema = jdbcTemplate.queryForObject("select current_schema()", String.class);
        Integer profileCount = jdbcTemplate.queryForObject("select count(*) from agent_profile", Integer.class);
        Integer documentCount = jdbcTemplate.queryForObject("select count(*) from knowledge_document", Integer.class);
        Integer modelCount = jdbcTemplate.queryForObject("select count(*) from model_catalog", Integer.class);

        assertThat(currentSchema).startsWith("kb_it_");
        assertThat(profileCount).isEqualTo(1);
        assertThat(documentCount).isEqualTo(2);
        assertThat(modelCount).isEqualTo(4);
        assertThat(agentProfileRepository.findByCode("default-qa")).isPresent();
        assertThat(agentProfileVersionRepository.findFirstByPublishedTrueOrderByUpdatedAtDesc()).isPresent();
        assertThat(knowledgeDocumentRepository.count()).isEqualTo(2);
        assertThat(ingestionJobRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldServeAuthenticatedUserChatAgainstRealDatabaseBackedApplication() {
        String accessToken = createUserToken("chat-user@example.com", "password123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> optionsResponse = testRestTemplate.exchange(
                RequestEntity.get(java.net.URI.create("http://localhost:" + port + "/api/app/chat/options"))
                        .headers(headers)
                        .build(),
                Map.class
        );
        ResponseEntity<ChatResponse> chatResponse = testRestTemplate.exchange(
                RequestEntity.post(java.net.URI.create("http://localhost:" + port + "/api/app/chat/messages"))
                        .headers(headers)
                        .body(Map.of(
                                "sessionId", "session-it-001",
                                "clientTraceId", "msg-it-001",
                                "query", "系统支持什么能力？",
                                "chatModel", "qwen-plus"
                        )),
                ChatResponse.class
        );
        ResponseEntity<Map> sessionResponse = testRestTemplate.exchange(
                RequestEntity.get(java.net.URI.create("http://localhost:" + port + "/api/app/chat/sessions/session-it-001"))
                        .headers(headers)
                        .build(),
                Map.class
        );

        assertThat(optionsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(optionsResponse.getBody()).containsEntry("defaultChatModel", "qwen-max");
        assertThat(optionsResponse.getBody().get("activeChatModel")).isIn("qwen-max", "qwen-plus");
        assertThat((java.util.List<?>) optionsResponse.getBody().get("models")).hasSize(2);

        assertThat(chatResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chatResponse.getBody()).isNotNull();
        assertThat(chatResponse.getBody().sessionId()).isEqualTo("session-it-001");
        assertThat(chatResponse.getBody().answer()).isNotBlank();
        assertThat(chatResponse.getBody().chatModel()).isEqualTo("qwen-plus");

        assertThat(sessionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionResponse.getBody()).containsEntry("sessionId", "session-it-001");
        java.util.List<Map<String, Object>> messages = (java.util.List<Map<String, Object>>) sessionResponse.getBody().get("messages");
        assertThat(messages).hasSizeGreaterThanOrEqualTo(2);
        assertThat(messages.get(0)).containsEntry("role", "user");
        assertThat(messages.get(1)).containsEntry("role", "assistant");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPersistAgentExecutionTraceAndExposeAdminTraceDetail() {
        String accessToken = createUserToken("trace-user@example.com", "password123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<ChatResponse> chatResponse = testRestTemplate.exchange(
                RequestEntity.post(java.net.URI.create("http://localhost:" + port + "/api/app/chat/messages"))
                        .headers(headers)
                        .body(Map.of(
                                "sessionId", "session-trace-001",
                                "clientTraceId", "msg-trace-001",
                                "query", "这个知识库系统的核心能力有哪些？",
                                "chatModel", "qwen-plus"
                        )),
                ChatResponse.class
        );

        assertThat(chatResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> tracesResponse = testRestTemplate.withBasicAuth("admin-it", "admin-it-pass").getForEntity(
                "http://localhost:" + port + "/api/admin/traces?sessionCode=session-trace-001&page=1&pageSize=10",
                Map.class
        );

        assertThat(tracesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tracesResponse.getBody()).isNotNull();
        List<Map<String, Object>> items = (List<Map<String, Object>>) tracesResponse.getBody().get("items");
        assertThat(items).isNotEmpty();
        Map<String, Object> trace = items.get(0);
        assertThat(trace).containsEntry("sessionCode", "session-trace-001");
        assertThat(trace.get("traceId")).isNotNull();
        assertThat(trace.get("status")).isEqualTo("COMPLETED");

        String traceId = String.valueOf(trace.get("traceId"));
        ResponseEntity<Map> detailResponse = testRestTemplate.withBasicAuth("admin-it", "admin-it-pass").getForEntity(
                "http://localhost:" + port + "/api/admin/traces/" + traceId,
                Map.class
        );

        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody()).isNotNull();
        assertThat(detailResponse.getBody()).containsKey("trace");
        assertThat((List<?>) detailResponse.getBody().get("agentTimeline")).isNotEmpty();
        assertThat((List<?>) detailResponse.getBody().get("backendSpans")).isNotEmpty();
        assertThat((List<?>) detailResponse.getBody().get("spans")).isNotEmpty();
        assertThat((List<?>) detailResponse.getBody().get("events")).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDeleteCompletedAgentExecutionTraceFromAdminEndpoint() {
        String accessToken = createUserToken("trace-delete-user@example.com", "password123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<ChatResponse> chatResponse = testRestTemplate.exchange(
                RequestEntity.post(java.net.URI.create("http://localhost:" + port + "/api/app/chat/messages"))
                        .headers(headers)
                        .body(Map.of(
                                "sessionId", "session-trace-delete-001",
                                "clientTraceId", "msg-trace-delete-001",
                                "query", "请总结这个系统的运行追踪能力",
                                "chatModel", "qwen-plus"
                        )),
                ChatResponse.class
        );

        assertThat(chatResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> tracesResponse = testRestTemplate.withBasicAuth("admin-it", "admin-it-pass").getForEntity(
                "http://localhost:" + port + "/api/admin/traces?sessionCode=session-trace-delete-001&page=1&pageSize=10",
                Map.class
        );

        assertThat(tracesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tracesResponse.getBody()).isNotNull();
        List<Map<String, Object>> items = (List<Map<String, Object>>) tracesResponse.getBody().get("items");
        assertThat(items).isNotEmpty();
        String traceId = String.valueOf(items.get(0).get("traceId"));

        Integer traceCountBeforeDelete = jdbcTemplate.queryForObject(
                "select count(*) from agent_execution_trace where trace_id = ?",
                Integer.class,
                traceId
        );
        Integer spanCountBeforeDelete = jdbcTemplate.queryForObject(
                "select count(*) from agent_execution_span where trace_id = ?",
                Integer.class,
                traceId
        );
        Integer eventCountBeforeDelete = jdbcTemplate.queryForObject(
                "select count(*) from agent_execution_event where trace_id = ?",
                Integer.class,
                traceId
        );
        Integer backendSpanCountBeforeDelete = jdbcTemplate.queryForObject(
                "select count(*) from agent_execution_backend_span where trace_id = ?",
                Integer.class,
                traceId
        );
        assertThat(traceCountBeforeDelete).isEqualTo(1);
        assertThat(spanCountBeforeDelete).isGreaterThan(0);
        assertThat(eventCountBeforeDelete).isGreaterThan(0);
        assertThat(backendSpanCountBeforeDelete).isGreaterThan(0);

        ResponseEntity<Map> deleteResponse = testRestTemplate.withBasicAuth("admin-it", "admin-it-pass").exchange(
                "http://localhost:" + port + "/api/admin/traces/" + traceId,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Map.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResponse.getBody()).containsEntry("message", "Trace deleted");

        ResponseEntity<Map> detailAfterDelete = testRestTemplate.withBasicAuth("admin-it", "admin-it-pass").getForEntity(
                "http://localhost:" + port + "/api/admin/traces/" + traceId,
                Map.class
        );
        assertThat(detailAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detailAfterDelete.getBody()).containsEntry("code", "TRACE_NOT_FOUND");

        Integer traceCountAfterDelete = jdbcTemplate.queryForObject(
                "select count(*) from agent_execution_trace where trace_id = ?",
                Integer.class,
                traceId
        );
        Integer spanCountAfterDelete = jdbcTemplate.queryForObject(
                "select count(*) from agent_execution_span where trace_id = ?",
                Integer.class,
                traceId
        );
        Integer eventCountAfterDelete = jdbcTemplate.queryForObject(
                "select count(*) from agent_execution_event where trace_id = ?",
                Integer.class,
                traceId
        );
        Integer backendSpanCountAfterDelete = jdbcTemplate.queryForObject(
                "select count(*) from agent_execution_backend_span where trace_id = ?",
                Integer.class,
                traceId
        );
        assertThat(traceCountAfterDelete).isZero();
        assertThat(spanCountAfterDelete).isZero();
        assertThat(eventCountAfterDelete).isZero();
        assertThat(backendSpanCountAfterDelete).isZero();
    }

    @Test
    void shouldProtectAdminEndpointsAndAllowBasicAuth() {
        ResponseEntity<Map> unauthorized = testRestTemplate.getForEntity(
                "http://localhost:" + port + "/api/admin/me",
                Map.class
        );
        ResponseEntity<Map> authorized = testRestTemplate.withBasicAuth("admin-it", "admin-it-pass").getForEntity(
                "http://localhost:" + port + "/api/admin/me",
                Map.class
        );

        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.getBody()).containsEntry("code", "UNAUTHORIZED");
        assertThat(unauthorized.getBody()).containsEntry("path", "/api/admin/me");
        assertThat(authorized.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authorized.getBody()).containsEntry("username", "admin-it");
    }

    @Test
    void shouldAllowAdminChangingPasswordWithoutBootstrapOverride() {
        String oldPassword = "admin-it-pass";
        String newPassword = "admin-it-pass-new";

        ResponseEntity<Map> beforeChange = testRestTemplate.withBasicAuth("admin-it", oldPassword).getForEntity(
                "http://localhost:" + port + "/api/admin/me",
                Map.class
        );
        assertThat(beforeChange.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> changeResponse = testRestTemplate.withBasicAuth("admin-it", oldPassword).postForEntity(
                "http://localhost:" + port + "/api/admin/me/password",
                Map.of(
                        "currentPassword", oldPassword,
                        "newPassword", newPassword
                ),
                Map.class
        );
        assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changeResponse.getBody()).containsEntry("message", "管理员密码修改成功");

        ResponseEntity<Map> oldPasswordResponse = testRestTemplate.withBasicAuth("admin-it", oldPassword).getForEntity(
                "http://localhost:" + port + "/api/admin/me",
                Map.class
        );
        ResponseEntity<Map> newPasswordResponse = testRestTemplate.withBasicAuth("admin-it", newPassword).getForEntity(
                "http://localhost:" + port + "/api/admin/me",
                Map.class
        );
        assertThat(oldPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(newPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> restoreResponse = testRestTemplate.withBasicAuth("admin-it", newPassword).postForEntity(
                "http://localhost:" + port + "/api/admin/me/password",
                Map.of(
                        "currentPassword", newPassword,
                        "newPassword", oldPassword
                ),
                Map.class
        );
        assertThat(restoreResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldLoadModelCatalogAndUpdateProfileVersion() {
        ResponseEntity<Map[]> modelCatalogResponse = testRestTemplate.withBasicAuth("admin-it", "admin-it-pass").getForEntity(
                "http://localhost:" + port + "/api/admin/model-catalogs",
                Map[].class
        );
        RequestEntity<Map<String, Object>> request = new RequestEntity<>(
                Map.of(
                        "chatModel", "qwen-plus",
                        "routingModel", "qwen-plus",
                        "embeddingModel", "text-embedding-v3",
                        "rerankModel", "gte-rerank",
                        "temperature", 0.4D,
                        "retrievalTopK", 8,
                        "reasoningBudget", 2
                ),
                HttpMethod.PUT,
                java.net.URI.create("http://localhost:" + port + "/api/admin/profile-versions/1")
        );
        ResponseEntity<Map> updateResponse = testRestTemplate.withBasicAuth("admin-it", "admin-it-pass").exchange(
                request,
                Map.class
        );

        assertThat(modelCatalogResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(modelCatalogResponse.getBody()).isNotNull();
        assertThat(modelCatalogResponse.getBody()).hasSize(4);
        assertThat(modelCatalogResponse.getBody()[0]).containsKeys("publicSelectable", "defaultForPublic");
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).containsEntry("chatModel", "qwen-plus");
        assertThat(updateResponse.getBody()).containsEntry("routingModel", "qwen-plus");
        assertThat(updateResponse.getBody()).containsEntry("retrievalTopK", 8);
    }

    @Test
    void shouldAllowPasswordLoginForSeededAdminFrontendAccount() {
        ResponseEntity<UserAuthResponse> response = testRestTemplate.postForEntity(
                "http://localhost:" + port + "/api/public/auth/login/password",
                Map.of(
                        "email", "admin@example.com",
                        "password", "admin123"
                ),
                UserAuthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().user().email()).isEqualTo("admin@example.com");
        assertThat(response.getBody().message()).isEqualTo("登录成功，欢迎回来");
    }

    @Test
    void shouldAllowDeletingSessionEvenWhenAssistantIsStreaming() {
        String email = "deleting-streaming@example.com";
        String accessToken = createUserToken(email, "password123");
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();

        String sessionId = "session-streaming-delete-it";
        ChatSession session = new ChatSession();
        session.setUserId(user.getId());
        session.setSessionCode(sessionId);
        session.setActiveProfileCode("default-qa");
        session.setStatus(ChatSessionStatus.ACTIVE);
        session.setTitle("删除测试");
        session.setSelectedChatModel("qwen-plus");
        chatSessionRepository.save(session);

        ChatTurn userTurn = new ChatTurn();
        userTurn.setUserId(user.getId());
        userTurn.setSessionCode(sessionId);
        userTurn.setMessageCode("user-turn-delete-it");
        userTurn.setClientMessageId("client-delete-it");
        userTurn.setRole("user");
        userTurn.setStatus(ChatMessageStatus.COMPLETED);
        userTurn.setContent("请总结系统能力");
        chatTurnRepository.save(userTurn);

        ChatTurn assistantTurn = new ChatTurn();
        assistantTurn.setUserId(user.getId());
        assistantTurn.setSessionCode(sessionId);
        assistantTurn.setMessageCode("assistant-turn-delete-it");
        assistantTurn.setRole("assistant");
        assistantTurn.setStatus(ChatMessageStatus.STREAMING);
        assistantTurn.setModelCode("qwen-plus");
        assistantTurn.setContent("正在生成中");
        chatTurnRepository.save(assistantTurn);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Void> deleteResponse = testRestTemplate.exchange(
                RequestEntity.delete(java.net.URI.create("http://localhost:" + port + "/api/app/chat/sessions/" + sessionId))
                        .headers(headers)
                        .build(),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chatSessionRepository.findByUserIdAndSessionCode(user.getId(), sessionId)).isEmpty();
        assertThat(chatTurnRepository.findByUserIdAndSessionCodeOrderByIdAsc(user.getId(), sessionId)).isEmpty();
    }

    @Test
    void shouldReturnStructuredBadRequestForInvalidModelCatalogPayload() {
        ResponseEntity<Map> response = testRestTemplate.withBasicAuth("admin-it", "admin-it-pass").postForEntity(
                "http://localhost:" + port + "/api/admin/model-catalogs",
                Map.of(
                        "code", "",
                        "displayName", "",
                        "provider", "dashscope",
                        "modelType", "CHAT",
                        "enabled", true,
                        "publicSelectable", true,
                        "defaultForPublic", true
                ),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(response.getBody()).containsEntry("path", "/api/admin/model-catalogs");
        Map<String, Object> fieldErrors = (Map<String, Object>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsKey("code");
        assertThat(fieldErrors).containsKey("displayName");
    }

    @Test
    void shouldCreateReviewRequestAndApproveDocument() throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "审核流程测试文档");
        body.add("visibilityType", "PUBLIC");
        body.add("markdown", new ByteArrayResource("""
                # 审核流程测试文档

                这是一个用于集成测试的文档。
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "review-test.md";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> uploadRequest = new HttpEntity<>(body, headers);

        ResponseEntity<Map> uploadResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .postForEntity("http://localhost:" + port + "/api/admin/documents/upload", uploadRequest, Map.class);

        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number reviewRequestId = (Number) uploadResponse.getBody().get("reviewRequestId");
        assertThat(reviewRequestId).isNotNull();

        Map detailBody = waitForReviewStatus(reviewRequestId.longValue(), "PENDING_REVIEW", 20, 100);
        assertThat(detailBody.get("status")).isEqualTo("PENDING_REVIEW");

        ResponseEntity<Map> approveResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .postForEntity(
                        "http://localhost:" + port + "/api/admin/document-reviews/" + reviewRequestId.longValue() + "/approve",
                        Map.of("reason", "集成测试通过"),
                        Map.class
                );
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approveResponse.getBody()).containsEntry("status", "APPROVED");

        ResponseEntity<Map[]> documentsResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .getForEntity("http://localhost:" + port + "/api/admin/documents", Map[].class);
        assertThat(documentsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(documentsResponse.getBody()).isNotNull();
        assertThat(java.util.Arrays.stream(documentsResponse.getBody())
                .anyMatch(document -> "审核流程测试文档".equals(document.get("title"))))
                .isTrue();
    }

    @Test
    void shouldCreateReviewRequestFromJsonUpload() throws Exception {
        Map<String, Object> payload = Map.of(
                "title", "填写式上传文档测试",
                "sourceFilename", "json-upload.md",
                "visibilityType", "PUBLIC",
                "sourceMarkdown", "# 填写式上传文档测试\n\n通过 JSON 提交审核单。",
                "extensionJson", "{\"source\":\"admin-form\"}"
        );

        ResponseEntity<Map> createResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .exchange(
                        RequestEntity.post(java.net.URI.create("http://localhost:" + port + "/api/admin/documents/upload-json"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(payload),
                        Map.class
                );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        Number reviewRequestId = (Number) createResponse.getBody().get("reviewRequestId");
        assertThat(reviewRequestId).isNotNull();
        assertThat(createResponse.getBody()).containsEntry("sourceFilename", "json-upload.md");

        Map detailBody = waitForReviewStatus(reviewRequestId.longValue(), "PENDING_REVIEW", 30, 100);
        assertThat(detailBody.get("status")).isEqualTo("PENDING_REVIEW");
        assertThat(detailBody.get("sourceFilename")).isEqualTo("json-upload.md");
    }

    @Test
    void shouldReuseSameAssetWhenPastingSameImageContent() throws Exception {
        byte[] imageBytes = "same-image-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ResponseEntity<Map> firstResponse = uploadPastedImage("paste-a.png", imageBytes);
        ResponseEntity<Map> secondResponse = uploadPastedImage("paste-b.png", imageBytes);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().get("md5")).isEqualTo(secondResponse.getBody().get("md5"));
        assertThat(firstResponse.getBody().get("url")).isEqualTo(secondResponse.getBody().get("url"));
        assertThat(firstResponse.getBody().get("objectKey")).isEqualTo(secondResponse.getBody().get("objectKey"));

        String md5 = String.valueOf(firstResponse.getBody().get("md5"));
        java.nio.file.Path uploadPath = java.nio.file.Path.of(environment.getRequiredProperty("kb.it.upload-path"));
        java.nio.file.Path stored = uploadPath.resolve("assets").resolve(md5 + ".png");
        assertThat(java.nio.file.Files.exists(stored)).isTrue();
        assertThat(java.nio.file.Files.readAllBytes(stored)).isEqualTo(imageBytes);
    }

    @Test
    void shouldRejectEditingDocumentWhenAnotherReviewIsInProgress() {
        Map<String, Object> payload = Map.of(
                "title", "文档编辑审核冲突测试",
                "sourceFilename", "conflict-test.md",
                "visibilityType", "PUBLIC",
                "sourceMarkdown", "# 文档编辑审核冲突测试\n\n第一次提交。",
                "extensionJson", "{}"
        );

        ResponseEntity<Map> firstResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .exchange(
                        RequestEntity.put(java.net.URI.create("http://localhost:" + port + "/api/admin/documents/1/source"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(payload),
                        Map.class
                );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> secondResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .exchange(
                        RequestEntity.put(java.net.URI.create("http://localhost:" + port + "/api/admin/documents/1/source"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(payload),
                        Map.class
                );
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondResponse.getBody()).containsEntry("code", "REVIEW_ALREADY_IN_PROGRESS");
    }

    @Test
    void shouldApproveEditReviewWithCaseInsensitiveDuplicateTags() throws Exception {
        Map<String, Object> editPayload = Map.of(
                "title", "Knowledge Box Agent 定义",
                "sourceFilename", "knowledge-box-agent-definition.md",
                "visibilityType", "AGENT_ONLY",
                "sourceMarkdown", "# Knowledge Box Agent 定义\n\n用于验证标签去重逻辑。",
                "extensionJson", "{}"
        );

        ResponseEntity<Map> createReviewResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .exchange(
                        RequestEntity.put(java.net.URI.create("http://localhost:" + port + "/api/admin/documents/2/source"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(editPayload),
                        Map.class
                );
        assertThat(createReviewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number reviewRequestId = (Number) createReviewResponse.getBody().get("id");
        assertThat(reviewRequestId).isNotNull();

        Map detailBody = waitForReviewStatus(reviewRequestId.longValue(), "PENDING_REVIEW", 30, 100);
        assertThat(detailBody.get("status")).isEqualTo("PENDING_REVIEW");

        ResponseEntity<Map> taxonomyResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .exchange(
                        RequestEntity.put(java.net.URI.create("http://localhost:" + port + "/api/admin/document-reviews/" + reviewRequestId.longValue() + "/taxonomy"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Map.of(
                                        "categoryName", "Agent定义",
                                        "tags", java.util.List.of("agent", "AGENT", "Agent")
                                )),
                        Map.class
                );
        assertThat(taxonomyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> approveResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .postForEntity(
                        "http://localhost:" + port + "/api/admin/document-reviews/" + reviewRequestId.longValue() + "/approve",
                        Map.of("reason", "标签去重验证"),
                        Map.class
                );
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approveResponse.getBody()).containsEntry("status", "APPROVED");
    }

    @Test
    void shouldFilterAndPaginateDocumentReviewsInDescOrder() throws Exception {
        Long firstReviewId = createJsonReviewAndWaitPending(
                "分页筛选测试-1",
                "review-filter-page-1.md",
                "# 分页筛选测试 1\n\n用于验证审核列表分页。"
        );
        Long secondReviewId = createJsonReviewAndWaitPending(
                "分页筛选测试-2",
                "review-filter-page-2.md",
                "# 分页筛选测试 2\n\n用于验证审核列表排序。"
        );

        ResponseEntity<Map> pageResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .getForEntity("http://localhost:" + port + "/api/admin/document-reviews?page=1&pageSize=2", Map.class);
        assertThat(pageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pageResponse.getBody()).isNotNull();
        assertThat(pageResponse.getBody()).containsEntry("page", 1);
        assertThat(pageResponse.getBody()).containsEntry("pageSize", 2);
        Number total = (Number) pageResponse.getBody().get("total");
        assertThat(total).isNotNull();
        assertThat(total.longValue()).isGreaterThanOrEqualTo(2L);
        List<Map<String, Object>> pageItems = (List<Map<String, Object>>) pageResponse.getBody().get("items");
        assertThat(pageItems).hasSize(2);
        Number firstId = (Number) pageItems.get(0).get("id");
        Number secondId = (Number) pageItems.get(1).get("id");
        assertThat(firstId.longValue()).isGreaterThan(secondId.longValue());
        assertThat(List.of(firstReviewId, secondReviewId)).contains(firstId.longValue());
        assertThat(List.of(firstReviewId, secondReviewId)).contains(secondId.longValue());

        ResponseEntity<Map> filteredResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .getForEntity("http://localhost:" + port + "/api/admin/document-reviews?status=PENDING_REVIEW&page=1&pageSize=10", Map.class);
        assertThat(filteredResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filteredResponse.getBody()).isNotNull();
        List<Map<String, Object>> filteredItems = (List<Map<String, Object>>) filteredResponse.getBody().get("items");
        assertThat(filteredItems).isNotEmpty();
        assertThat(filteredItems).allMatch(item -> "PENDING_REVIEW".equals(item.get("status")));
        assertThat(filteredItems.stream().map(item -> ((Number) item.get("id")).longValue()))
                .contains(firstReviewId, secondReviewId);
    }

    private Long createJsonReviewAndWaitPending(String title, String sourceFilename, String sourceMarkdown) throws InterruptedException {
        Map<String, Object> payload = Map.of(
                "title", title,
                "sourceFilename", sourceFilename,
                "visibilityType", "PUBLIC",
                "sourceMarkdown", sourceMarkdown,
                "extensionJson", "{}"
        );
        ResponseEntity<Map> createResponse = testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .exchange(
                        RequestEntity.post(java.net.URI.create("http://localhost:" + port + "/api/admin/documents/upload-json"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(payload),
                        Map.class
                );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        Number reviewRequestId = (Number) createResponse.getBody().get("reviewRequestId");
        assertThat(reviewRequestId).isNotNull();
        Map detailBody = waitForReviewStatus(reviewRequestId.longValue(), "PENDING_REVIEW", 30, 100);
        assertThat(detailBody.get("status")).isEqualTo("PENDING_REVIEW");
        return reviewRequestId.longValue();
    }

    private ResponseEntity<Map> uploadPastedImage(String filename, byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        return testRestTemplate
                .withBasicAuth("admin-it", "admin-it-pass")
                .postForEntity("http://localhost:" + port + "/api/admin/documents/paste-image", request, Map.class);
    }

    private String createUserToken(String email, String rawPassword) {
        UserAccount userAccount = userAccountRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    UserAccount created = new UserAccount();
                    created.setEmail(email);
                    created.setPasswordHash(passwordEncoder.encode(rawPassword));
                    created.setEnabled(true);
                    return userAccountRepository.save(created);
                });
        return jwtTokenService.issue(userAccount).token();
    }

    private Map waitForReviewStatus(Long reviewRequestId, String expectedStatus, int maxAttempts, long sleepMs) throws InterruptedException {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            ResponseEntity<Map> detailResponse = testRestTemplate
                    .withBasicAuth("admin-it", "admin-it-pass")
                    .getForEntity("http://localhost:" + port + "/api/admin/document-reviews/" + reviewRequestId, Map.class);
            if (detailResponse.getStatusCode() == HttpStatus.OK && detailResponse.getBody() != null) {
                String status = String.valueOf(detailResponse.getBody().get("status"));
                if (expectedStatus.equals(status) || "FAILED".equals(status)) {
                    return detailResponse.getBody();
                }
            }
            Thread.sleep(sleepMs);
        }
        throw new AssertionError("Review request did not reach status " + expectedStatus + " in time");
    }
}
