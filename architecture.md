# Knowledge Box 架构文档

## 1. 文档目的

- 本文档用于说明 Knowledge Box 项目当前真实生效的架构，帮助维护者在不通读整个仓库的前提下，理解 AI 编写的代码、定位问题并进行扩展。
- 最近更新时间：`2026-03-11`
- 文档范围：当前 `frontend/`、`backend/` 下的活跃代码，以及项目级启动与配置组织方式。

## 2. 项目概览

Knowledge Box 当前是一个包含 React 前端和 Spring Boot 后端的项目。当前活跃形态包括：

- 一个面向普通用户的聊天应用：用户登录后向后端发起流式问答请求
- 一个面向管理员的后台：查看和维护模型、配置版本、文档、集成、Hooks、Trace 等页面
- 一个后端单体服务：负责认证、文档导入、聊天会话持久化、知识检索以及基于 AgentScope 的大模型编排

高层请求流转如下：

1. 前端向后端发起普通 JSON 请求或 SSE 流式请求。
2. 后端根据接口类型使用 Basic Auth 或 JWT 做鉴权。
3. 对聊天请求，后端会先持久化会话与消息，再构建 ReAct Agent、注册知识库检索工具、执行检索与模型推理，并通过 SSE 持续回传事件。
4. 对文档上传请求，后端会处理资源文件、规范化 Markdown、切分文档块、保存元数据，并在启用向量检索时写入 pgvector。

## 3. 仓库结构

当前顶层活跃目录：

- `backend/`：Spring Boot 后端模块及其资源文件
- `frontend/`：React 19 + Vite 单页应用
- `.codex/skills/`：仓库级 skill，用于沉淀维护流程与项目知识

重要顶层文件：

- `pom.xml`：根 Maven 聚合工程；当前只聚合 `backend/`
- `README.md`：项目总览与本地启动说明
- `rule.md`：稳定的仓库约束
- `progress.md`：当前功能与验证状态

特殊目录说明：

- 后端已拆分为多模块：`backend/backend-app`（启动与控制层）、`backend/backend-service`（业务与配置）、`backend/backend-repository`（数据访问）、`backend/backend-domain`（实体与枚举）。
- 启动类位于 `backend/backend-app`，其余模块通过 Maven 依赖装配为同一 Spring Boot 运行时。
- 运行时上传目录由 `knowledge-box.storage.local-base-path` 决定；当前默认值指向 `backend/uploads`。仓库中出现的某些上传目录可能是运行产物，不是源代码结构。

## 4. 前端架构

### 4.1 运行时与启动

前端技术栈：

- `React 19`
- `Vite`
- `TypeScript`
- `Ant Design 5`
- `@ant-design/pro-components`
- `@tanstack/react-query`

启动入口：

- `frontend/src/main.tsx`

`main.tsx` 当前负责：

- 创建 React 根节点
- 配置 Ant Design `ConfigProvider`
- 注入 `QueryClientProvider`
- 注入 `RouterProvider`
- 引入全局样式 `frontend/src/styles/global.css`

### 4.2 路由结构与鉴权守卫

路由入口：

- `frontend/src/app/router.tsx`

当前路由分组：

- `/login`：用户登录 / 注册页
- `/`：登录后的用户聊天页
- `/admin/login`：管理员登录页
- `/admin/*`：管理后台页面

当前路由守卫直接写在 `router.tsx` 中：

- `RequireUserAuth`：要求本地存在用户 JWT 会话
- `RedirectAuthenticatedUser`：已登录用户不再进入 `/login`
- `RedirectAuthenticatedAdmin`：已登录管理员不再进入 `/admin/login`

### 4.3 请求层与前后端交互

前端请求层的真实入口：

- `frontend/src/lib/api.ts`

当前关键点：

- `API_BASE_URL` 来自 `import.meta.env.VITE_API_BASE_URL`，默认值是 `http://localhost:8080`
- `buildApiUrl(path)` 统一拼接后端地址
- `requestJson()` 统一封装 `fetch`、JSON 解析、错误归一化和鉴权头注入
- `authMode` 支持 `none`、`user`、`admin`
- 管理端请求使用保存在本地的 Basic Auth
- 用户请求使用保存在本地的 Bearer Token
- 请求返回 `401` 时会清理本地登录态，并自动跳转到 `/login` 或 `/admin/login`

`api.ts` 当前实际维护的接口分组如下：

- 公共认证接口：
  - `/api/public/auth/send-code`
  - `/api/public/auth/register`
  - `/api/public/auth/login/code`
  - `/api/public/auth/login/password`
- 用户应用接口：
  - `/api/app/me`
  - `/api/app/chat/options`
  - `/api/app/chat/sessions`
  - `/api/app/chat/sessions/{sessionId}`
  - `/api/app/chat/messages/stream`
  - `/api/app/chat/sessions/{sessionId}/messages/{messageId}/stream`
- 管理端接口：
  - `/api/admin/dashboard`
  - `/api/admin/profile-versions`
  - `/api/admin/model-catalogs`
  - `/api/admin/documents`
  - `/api/admin/ingestion-jobs`
  - `/api/admin/tools`
  - `/api/admin/mcp-servers`
  - `/api/admin/skills`
  - `/api/admin/hooks`
  - `/api/admin/traces`
  - `/api/admin/documents/upload`

前端环境变量位置：

- `frontend/.env.example`

当前示例变量：

- `VITE_API_BASE_URL=http://localhost:8080`

Vite 配置位置：

- `frontend/vite.config.ts`

当前开发服务器没有配置后端代理，前端访问哪个后端完全由 `VITE_API_BASE_URL` 决定。

### 4.4 用户登录态

前端登录态工具文件：

- `frontend/src/lib/auth.ts`

当前客户端本地存储项包括：

- `knowledge-box-admin-basic-auth`：管理员 Basic Auth token
- `knowledge-box-user-auth`：用户 access token、过期时间和用户信息
- `knowledge-box-active-session:{userId}`：当前用户最后一次活跃的聊天会话 id

这意味着当前前端登录态完全是浏览器本地状态，没有接入额外的全局状态管理库。

### 4.5 前端 SSE 聊天链路

相关文件：

- `frontend/src/features/chat/PublicChatPage.tsx`
- `frontend/src/lib/sse.ts`

当前流式接口路径：

- `POST /api/app/chat/messages/stream`
- `GET /api/app/chat/sessions/{sessionId}/messages/{messageId}/stream`

前端流式处理流程：

1. `PublicChatPage` 先创建乐观用户消息和助手占位消息。
2. 然后通过 `streamJsonSse()` 发起 SSE 请求。
3. `streamJsonSse()` 负责解析原始响应流中的 `event:` 和 `data:` 行。
4. 页面在 `normalizeStreamEvent()` 中同时兼容 SSE `eventName` 与负载里的 `type` 字段；后端新增字段一律按可选字段处理，未知事件类型不会直接抛错。
5. 页面状态会把增量文本、思考步骤、工具调用、引用信息、完成态或失败态合并回对应助手消息；未知轻量事件会被折叠为 reasoning 区中的提示文本。
6. 会话摘要中的 `pending` 当前只依据助手消息是否处于 `PENDING` 或 `STREAMING`，失败消息不会再被误判为“仍在生成中”。
7. 中文输入法场景下，`Input.TextArea` 通过 `isComposingRef`、`event.nativeEvent.isComposing` 与 `keyCode === 229` 共同拦截组合输入阶段的 Enter，避免选词时误发送。
8. `loadSessionDetail()` 以及当前活跃会话的 `useEffect` 会自动识别未完成助手消息，并调用 `resumeStream(...)` 恢复流式订阅。
9. 删除会话前，前端会先中止该会话对应的本地 `AbortController`，再调用后端删除接口。

### 4.6 前端代码组织

当前目录分层：

- `frontend/src/app/`：路由入口
- `frontend/src/features/auth/`：用户登录 / 注册页面
- `frontend/src/features/chat/`：公开聊天 UI 与 Markdown 渲染
- `frontend/src/features/admin/`：管理后台页面
- `frontend/src/layouts/`：后台布局壳
- `frontend/src/lib/`：API、鉴权、SSE、共享类型、错误处理
- `frontend/src/styles/`：全局样式

当前维护层面的事实：

- `PublicChatPage` 是前端最重的交互组件；会话创建、乐观更新、恢复流、删除会话、SSE 合并等核心逻辑都集中在这里。
- `AdminLayout` 提供管理后台整体布局，当前以本地 Basic Auth token 是否存在作为后台路由保护依据。
- `ProfileVersionsPage` 已是 Agent 配置管理的真实落点之一：`routingModel` 已进入列表展示、编辑表单、前端类型与更新 payload，并在前端表单层要求必填。

## 5. 后端架构

### 5.1 运行时与模块形态

后端技术栈：

- `Java 21`
- `Spring Boot 3.5.6`
- `Spring Security`
- `Spring Data JPA`
- `Liquibase`
- `Redis`
- `Spring Mail`
- `pgvector`（通过 Spring AI VectorStore 接入）
- `AgentScope Java 1.0.9`
- `Spring AI Alibaba DashScope`

启动入口：

- `backend/backend-app/src/main/java/com/knowledgebox/KnowledgeBoxApplication.java`

项目级配置绑定类：

- `backend/backend-service/src/main/java/com/knowledgebox/config/KnowledgeBoxProperties.java`

### 5.2 活跃分层

当前后端真实生效的分层结构如下：

- `web/*`：HTTP 控制器
- `service/*`：业务编排与集成逻辑
- `repository/*`：JPA Repository
- `domain/*`：实体与枚举
- `config/*`：Bean 装配、安全、CORS、向量存储配置
- `security/*`：JWT 解析与当前用户访问
- `api/*`：接口 DTO 和视图模型

### 5.3 面向前端的后端入口

用户侧与公共接口控制器：

- `web/publicapi/PublicAuthController`
  - `POST /api/public/auth/send-code`
  - `POST /api/public/auth/register`
  - `POST /api/public/auth/login/code`
  - `POST /api/public/auth/login/password`
  - `GET /api/app/me`
- `web/publicapi/PublicChatController`
  - `GET /api/public/chat/options`
- `web/publicapi/UserChatController`
  - `GET /api/app/chat/options`
  - `GET /api/app/chat/sessions`
  - `GET /api/app/chat/sessions/{sessionId}`
  - `DELETE /api/app/chat/sessions/{sessionId}`
  - `POST /api/app/chat/messages`
  - `POST /api/app/chat/messages/stream`
  - `GET /api/app/chat/sessions/{sessionId}/messages/{messageId}/stream`

管理端控制器：

- `web/admin/AdminController`
  - `/api/admin/me`
  - `/api/admin/dashboard`
  - `/api/admin/profile-versions`
  - `/api/admin/model-catalogs`
  - `/api/admin/documents`
  - `/api/admin/ingestion-jobs`
  - `/api/admin/tools`
  - `/api/admin/mcp-servers`
  - `/api/admin/skills`
  - `/api/admin/hooks`
  - `/api/admin/traces`
- `web/admin/AdminDocumentUploadController`
  - `POST /api/admin/documents/upload`

全局异常出口：

- `web/error/GlobalApiExceptionHandler`

### 5.4 安全设计

安全入口：

- `backend/backend-service/src/main/java/com/knowledgebox/config/SecurityConfig.java`

当前接口权限边界：

- `/api/admin/**`：要求 `ROLE_ADMIN`
- `/api/app/**`：要求 `ROLE_USER`
- `/api/public/chat/**`：当前除 options 接口外，整体仍偏向用户态访问
- `/api/public/auth/**`：开放
- `/uploads/**` 和健康检查：开放

当前认证机制：

- 管理端接口：基于配置中的管理员账号密码，走 HTTP Basic Auth
- 用户接口：基于 `JwtTokenService` 和 `JwtAuthenticationFilter`，走 JWT Bearer Token

当前用户解析相关类：

- `security/CurrentUserAccessor`
- `security/CurrentUser`

对 SSE 很重要的安全细节：

- `JwtAuthenticationFilter.shouldNotFilterAsyncDispatch()` 返回 `false`，因此异步分派和 SSE 相关请求续流时，JWT 过滤器仍然会参与鉴权。

### 5.5 后端按领域划分的服务

认证相关：

- `service/auth/UserAuthService`
- `service/auth/EmailVerificationService`
- `service/auth/VerificationMailService`
- `security/JwtTokenService`

聊天与 AgentScope 编排：

- `service/chat/ChatOrchestrator`
- `service/chat/ConversationMemoryService`
- `service/chat/ChatStreamBroker`
- `service/chat/AgentTraceService`
- `service/chat/KnowledgeBaseSearchTool`
- `service/chat/KnowledgeBaseRetrievalService`
- `service/chat/KnowledgeBaseIndexingService`

文档导入：

- `service/document/DocumentIngestionService`
- `service/document/LocalStorageService`

管理后台服务：

- `service/admin/AdminQueryService`
- `service/admin/AdminCommandService`
- `service/admin/PublishedProfileRoutingModelValidator`

管理端当前的真实状态说明：

- 文档上传、模型目录写入、配置版本更新已经接到真实服务和仓储层
- 但 `AdminQueryService` 中仍有一部分读接口返回的是硬编码或占位数据，例如 documents、ingestion jobs、tools、MCP servers、skills、hooks、traces

## 6. 配置矩阵

### 6.1 共享配置与本地配置

共享默认配置：

- `backend/backend-app/src/main/resources/application.yml`

本地样例配置：

- `backend/backend-app/src/main/resources/application-local.yml.example`

用户本机配置：

- `backend/backend-app/src/main/resources/application-local.yml`

维护规则：

- `application-local.yml` 属于用户本机环境状态，不应随意改写。
- 如果共享配置结构变化，优先同步更新 `application.yml`、`application-local.yml.example` 和相关文档。

### 6.2 当前项目实际使用的 Spring 配置组

- `server.port`：后端 HTTP 端口，当前默认 `8080`
- `management.endpoints.web.exposure.include`：对外暴露的 actuator 端点
- `spring.datasource.*`：PostgreSQL 连接
- `spring.data.redis.*`：Redis 连接，用于认证和运行时状态
- `spring.mail.*`：验证码邮件 SMTP 配置
- `spring.ai.dashscope.*`：DashScope API Key、聊天模型默认值、Embedding 默认值
- `spring.liquibase.*`：数据库迁移入口

### 6.3 `knowledge-box.*` 配置组

配置绑定的真实来源：

- `KnowledgeBoxProperties`

当前配置组及含义：

- `knowledge-box.admin.*`
  - 管理员用户名和密码
- `knowledge-box.auth.*`
  - JWT 密钥、issuer、token TTL、验证码 TTL、发送冷却时间
- `knowledge-box.mail.*`
  - 验证码邮件发件地址与发件人昵称
- `knowledge-box.redis.keys.*`
  - Redis key 命名空间，按认证、聊天、限流分组
- `knowledge-box.storage.*`
  - 存储提供者、本地目录、对外访问前缀
- `knowledge-box.chat.*`
  - 聊天默认参数，如 `top-k`、`history-turns`、`stub-responses`、`stream-delay`
- `knowledge-box.chat.knowledge-base-routing.*`
  - 查询意图路由相关配置
  - `enabled`：是否启用“规则优先 + 轻量模型判定”的知识库路由
  - `force-enable-regexes`：命中后强制启用知识库工具
  - `force-disable-regexes`：命中后强制跳过知识库工具
- `knowledge-box.retrieval.*`
  - 检索参数，如 top-k、相似度阈值、chunk size、向量开关、embedding 维度、vector schema、vector table
- `knowledge-box.web.allowed-origins`
  - `WebConfig` 使用的 CORS 白名单

### 6.4 直接影响运行行为的配置类

- `SecurityConfig`：管理员鉴权与 JWT 启动前置要求
- `WebConfig`：CORS 和 `/uploads/**` 静态资源映射
- `AiConfig`：在 `knowledge-box.retrieval.vector-enabled=true` 时装配 pgvector `VectorStore`

## 7. 大模型与知识检索链路

### 7.1 聊天入口

用户侧聊天主入口：

- `UserChatController.stream()`

当前流程：

1. 控制器接收 `ChatMessageRequest`
2. `ChatOrchestrator.stream()` 确保会话存在，并追加用户消息
3. 创建或查找对应的助手占位消息
4. 通过 `ChatStreamBroker` 建立 SSE 订阅
5. 如果需要，则启动后台生成任务

### 7.2 会话与消息持久化

持久化主服务：

- `ConversationMemoryService`

当前职责：

- 确保聊天会话存在
- 追加用户消息
- 创建助手占位消息
- 将助手消息更新为 `STREAMING`、`COMPLETED` 或 `FAILED`
- 查询会话列表
- 查询会话详情
- 删除会话
- 定位可恢复的助手消息

持久化仓储：

- `ChatSessionRepository`
- `ChatTurnRepository`

### 7.3 ReAct Agent 创建、查询路由与模型接入

Agent 创建位置：

- `ChatOrchestrator.createReActAgent()`
- `ChatOrchestrator.routeQuery()`
- `ChatOrchestrator.invokeRoutingModel()`

当前实现方式：

- 先读取当前已发布的 `AgentProfileVersion`
- 解析本轮实际回答模型 `chatModel`，以及 profile 中单独维护的 `routingModel`
- `routeQuery()` 先匹配 `knowledge-box.chat.knowledge-base-routing.force-enable-regexes`
- 若未命中，再匹配 `force-disable-regexes`
- 若仍未命中，则调用 `routingModel` 对当前 query 做轻量分类，只接受 `NEED_KB` 或 `NO_KB`
- 路由异常、空输出或非法输出会安全兜底到 `NEED_KB`
- 创建 `Toolkit`
- 仅在路由结果要求启用知识库时注册 `KnowledgeBaseSearchTool`
- 构建 `ReActAgent`
- 系统提示词来自当前已发布的 `AgentProfileVersion`，并按“启用知识库工具 / 禁用知识库工具”生成两套提示词
- 模型由 `buildDashScopeModel()` 创建为 `DashScopeChatModel`
- 回答模型生成参数来自 profile 中的温度、推理预算和当前请求指定的 chat model
- 路由模型由 `buildRoutingClassifierModel()` 创建，固定 `stream=false`、`temperature=0`、`thinkingBudget=0`，只做低成本分类

模型 API Key 来源：

- `spring.ai.dashscope.api-key`

如果 API Key 缺失，`ChatOrchestrator.resolveDashScopeApiKey()` 会直接抛出服务不可用错误。

当前与管理端联动的关键事实：

- `AgentProfileVersion.routingModel` 已是数据库实体字段，并由 Liquibase `db.changelog-007-agent-profile-routing-model.xml` 负责迁移与历史回填。
- `AdminCommandService.updateProfileVersion(...)` 会校验 `routingModel` 必须是已启用的 `CHAT` 模型。
- `PublishedProfileRoutingModelValidator` 会在应用启动期扫描所有已发布 profile；如果 `routingModel` 为空，或指向未启用的 `CHAT` 模型，应用会直接启动失败。

### 7.4 检索与 Tool 链路

Agent Tool：

- `KnowledgeBaseSearchTool.searchKnowledgeBase()`

当前职责：

- 记录本次 tool call 到 `ChatExchangeContext`
- 通过 `KnowledgeBaseRetrievalService` 执行检索
- 将命中的检索结果写入 `ChatExchangeContext`
- 记录 retrieval trace
- 将格式化后的知识片段返回给 Agent

`KnowledgeBaseRetrievalService.search()` 当前检索回退顺序：

1. 通过 `VectorStore` 执行 pgvector 相似度检索
2. 通过 `DocumentChunkRepository.searchByText(...)` 执行 PostgreSQL 文本检索
3. 对所有 chunk 执行内存关键词打分

当前与查询路由相关的额外事实：

- 当 `routeQuery()` 判定本轮不需要知识库时，`createReActAgent()` 不会注册 `searchKnowledgeBase`。
- `buildSystemPrompt()` 会同步告知模型“本轮明确禁用知识库工具”，避免提示词还在要求调用不存在的工具。
- `shouldRunFallbackRetrieval(...)` 会在本轮启用知识库时才允许 fallback 检索；通用问题路由不会再无条件补跑检索。

索引写入路径：

- `DocumentIngestionService` 负责切分 chunk 并保存
- `KnowledgeBaseIndexingService.index(...)` 在向量能力启用时写入向量文档
- `KnowledgeBaseIndexingService.run(...)` 在应用启动时同步所有已有 chunk

### 7.5 SSE 事件流

SSE 广播组件：

- `ChatStreamBroker`

生成主循环：

- `ChatOrchestrator.generate(...)`

当前行为包括：

- 消费 `agent.stream(...)` 产生的事件
- `buildStreamOptions()` 当前订阅 `EventType.ALL` 与 `EventType.AGENT_RESULT`，并打开 reasoning / acting / summary 的 chunk 级输出
- `consumeAgentEvent(...)` 已显式处理 `REASONING`、`TOOL_RESULT`、`HINT`、`SUMMARY`、`AGENT_RESULT`、`ALL`
- `REASONING` 会做节流、去重与末尾补发，避免前端思考区抖动
- `TOOL_RESULT` 会实时写入 tool call 进度，并折叠到 reasoning 区域展示
- `HINT` 会作为轻量提示写入 reasoningSteps
- `SUMMARY` 先通过 `resolveDeltaFromCurrentContent(...)` 把“全量快照”转为真正增量；若一次返回大段文本，会按 `STREAM_CHUNK_SIZE` 小片切分并带轻量节奏控制后再推送
- `AGENT_RESULT` 保存最终消息对象，作为回答兜底来源
- `ALL` 仅作为标记事件记录 debug 日志
- 持续把流式状态写回数据库
- 持续向前端推送 SSE 增量事件和最终状态
- 支持按 message id 恢复订阅
- 当订阅方重连时，如果当前消息已经有内容或状态，会先发送 snapshot 事件
- 通过 `runningTasks` 防止同一助手消息重复生成
- 当消息仍处于 `PENDING` 或 `STREAMING`，但后台任务已不存在时，恢复链路可以自动重启生成任务
- 删除会话前，会先取消相关生成任务并关闭订阅中的 SSE 连接
- `QUERY_ROUTED` trace 会记录本轮是否启用知识库、命中规则、来源（`rule` / `model` / `model-fallback` / `config`）、`routingModel` 与轻量模型原始输出
- `ANSWER_COMPLETED` trace 会补充 `eventTypeCounts`，便于排查“某类事件没有产出 / 产出异常”的问题
- 取消或删除会话时，`ReactiveException(InterruptedException)`、`Closed by interrupt`、`CancellationException` 等会被视为正常取消，不再落成失败消息

## 8. 核心功能链路

### 8.1 用户登录与令牌链路

入口：

- `PublicAuthController`

当前流程：

1. `send-code` 调用 `UserAuthService.sendLoginCode()`
2. `EmailVerificationService` 校验发送冷却与验证码状态，并使用 Redis 记录相关数据
3. `VerificationMailService` 负责发信
4. `register`、`login/code`、`login/password` 最终都进入 `UserAuthService`
5. `UserAuthService` 更新 `UserAccount` 后，通过 `JwtTokenService` 签发 JWT
6. 前端通过 `frontend/src/lib/auth.ts` 保存 token

### 8.2 用户聊天流式链路

前端相关：

- `PublicChatPage`
- `api.ts`
- `sse.ts`

后端相关：

- `UserChatController`
- `ChatOrchestrator`
- `ConversationMemoryService`
- `ChatStreamBroker`

当前流程：

1. 前端发起 `POST /api/app/chat/messages/stream`
2. 后端持久化用户问题与助手占位消息
3. `ChatOrchestrator.routeQuery()` 先决定本轮是否启用知识库工具
4. 后台任务执行“带工具”或“无工具”的 ReAct Agent 链路
5. 如启用知识库，则按“向量检索 -> PostgreSQL 文本检索 -> 内存关键词检索”回退；如禁用知识库，则跳过工具注册与 fallback 检索
6. SSE 增量事件持续更新前端助手消息，并兼容未知事件类型的向后扩展
7. 如果中断，前端会根据当前消息状态尝试恢复流式订阅；若后端发现任务已不存在，会自动按历史问题重启生成

### 8.3 会话历史与删除链路

入口：

- `GET /api/app/chat/sessions`
- `GET /api/app/chat/sessions/{sessionId}`
- `DELETE /api/app/chat/sessions/{sessionId}`

后端服务：

- `ConversationMemoryService`

当前说明：

- 删除会话前，`ChatOrchestrator.deleteSession()` 会先调用 `cancelSessionTasks()`
- 后端会中断相关生成线程，并主动关闭对应 SSE 订阅，再删除会话与消息
- 前端也会先中止本地流控制器，再发起删除请求
- 前端还会把最后一次活跃会话 id 记录到本地存储中

### 8.4 管理端文档上传与索引链路

入口：

- `AdminDocumentUploadController.upload()`

当前流程：

1. 管理员上传 Markdown 和可选资源文件
2. `DocumentIngestionService` 读取 Markdown，并通过 `StorageService` 上传资源文件
3. Markdown 中的本地图片引用会被改写为上传后的访问路径
4. 后端会把规范化后的 Markdown 写入本地存储
5. 同时持久化 `KnowledgeDocument`、`DocumentChunk` 和 `IngestionJob`
6. 如果启用了向量能力，则由 `KnowledgeBaseIndexingService` 写入向量数据

### 8.5 管理端模型 / 配置管理链路

入口：

- `AdminController`

当前流程：

- 查询类操作主要由 `AdminQueryService` 提供
- 配置版本更新、模型目录创建与更新由 `AdminCommandService` 负责
- 底层持久化通过 `repository/` 中的仓储层完成
- `ProfileVersionsPage` 编辑 `routingModel` 时，只允许从“已启用的 CHAT 模型”中选择，并默认回填 `record.routingModel ?? record.chatModel`
- 后端在 `AdminCommandService` 与启动期 `PublishedProfileRoutingModelValidator` 两处共同保证 `routingModel` 合法

当前实现边界：

- profile version update 和 model catalog create/update 是真实写路径
- integrations、hooks、traces，以及部分 document / job 展示仍更偏向后台骨架而不是完整闭环的运营后台

## 9. 特殊说明与维护注意事项

- `local` profile 必须启用，否则 `application-local.yml` 不会生效。
- 后端启动前必须显式提供 `knowledge-box.admin.password` 和 `knowledge-box.auth.jwt-secret`。
- `application-local.yml` 属于用户本机配置，不应被自动覆盖。
- 在 AgentScope Tool 方法里，`@ToolParam(name = "...")` 必须显式填写。
- `com.knowledgebox.app.*` 目前没有活跃代码，不要把它当成当前运行时代码结构。
- 前端访问哪个后端完全由 `VITE_API_BASE_URL` 决定，当前不存在 Vite 代理层帮你隐藏后端地址。
- `/uploads/**` 由 `WebConfig` 根据 `knowledge-box.storage.local-base-path` 映射为静态资源访问路径。
- 当前聊天检索不是纯向量检索；它会按“向量检索 -> PostgreSQL 文本检索 -> 内存关键词检索”的顺序回退。
- `routingModel` 不是展示字段，而是运行时必需配置；已发布 profile 若缺失或引用失效模型，应用会在启动阶段直接失败。
- `knowledge-box.chat.knowledge-base-routing.force-disable-regexes` 当前只应放“明显通用问题”规则；误配会直接让项目内问题绕过知识库。
- `frontend/src/lib/api.ts` 中仍保留了一个 `api.chat()`，它指向 `/api/public/chat`。但当前真实聊天主链路和当前活跃后端控制器都走的是 `/api/app/chat/*`。如果后续没有重新接回对应控制器，应把它视为遗留 helper。
- 会话摘要中的 `pending` 当前只表示 `PENDING` 或 `STREAMING`，失败消息不应再被理解为“仍在进行中”。
- 前端流式协议按“兼容新增字段 / 容忍未知事件”设计；如果后端新增事件类型，优先复用 reasoning/tool 展示，不要先在前端写死强校验。
- 中文输入法 Enter 发送逻辑已经做过组合输入保护；维护聊天输入框时不要移除 `isComposing` 相关判断，否则会回归。
- 删除或主动取消回答时，看到 `InterruptedException`、`Closed by interrupt` 等中断异常，不应直接视为真实故障，应先按正常取消链路排查。

## 10. 变更记录

- `2026-03-11`：创建首版架构文档，梳理当前前端、后端、配置矩阵以及 AgentScope / RAG 聊天链路。
- `2026-03-11`：根据最近的恢复流、删除流式会话、修正 pending 语义等改动，补充前端接口分组、聊天恢复与删除链路、SSE snapshot / 任务重启机制，以及管理端占位数据的现状说明。
- `2026-03-11`：根据当前代码刷新文档，补充查询意图路由、`routingModel` 配置与启动校验、AgentScope 全事件分支处理、`SUMMARY` 快照转增量、前端 SSE 向后兼容、IME 回车保护，以及删除中断按正常取消处理等实现细节。
