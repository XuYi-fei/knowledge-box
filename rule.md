# Rule

## Before Starting

- Read `progress.md` first, then read this file.
- Read only the modules relevant to the task; avoid broad, unfocused scanning.
- If the task changes behavior, config, or delivery scope, plan to update `progress.md` before finishing.

## Development Constraints

- Do not hardcode secrets, passwords, JWT keys, SMTP credentials, Redis credentials, database credentials, or real API keys.
- Do not hardcode environment-specific endpoints or origins in Java or frontend source when they should be configurable.
- Prefer explicit configuration in `application-local.yml`, `application-local.yml.example`, environment variables, or documented startup parameters.
- Do not overwrite values already configured by the user in `application-local.yml` unless the user explicitly asks to modify that file.
- `backend/backend-app/src/main/resources/application-local.yml` 视为本地敏感配置文件，必须保持在 `.gitignore` 中，不参与版本追踪。
- If shared config structure changes, prefer updating `application.yml`, `application-local.yml.example`, and README; only touch `application-local.yml` when the change is explicitly local-env related and user-approved.
- When changing backend config shape, keep `README.md` and local example config in sync.
- Keep admin-only settings, local-only settings, and shared defaults clearly separated.
- Keep new project rules short and durable; transient task notes belong in `progress.md`, not here.

## Delivery Expectations

- After each meaningful feature, bugfix, or infra change, update `progress.md`.
- After each independent feature is completed, also add a concise release note to the About tab by inserting data for `about_release_note` through a new additive database changelog/script.
- When creating git commits in this repo, use Chinese commit messages unless the user explicitly asks for another language.
- Distinguish "已完成" from "已验证无误". Only move items into verified when you actually ran a validation path.
- Prefer targeted verification that matches the change surface. Record what was verified.
- Do not revert unrelated user changes.

## Current Pitfalls

- `application-local.yml` only takes effect when the `local` profile is active.
- Backend startup now requires explicit `knowledge-box.admin.password` and `knowledge-box.auth.jwt-secret`.
- Redis, SMTP, and CORS are expected to be configuration-driven rather than fixed in source.
- QQ 邮箱发送验证码时，`spring.mail.password` 必须填 SMTP 授权码，不是网页登录密码。
- PostgreSQL 集成测试依赖本机 PostgreSQL 与 `pgvector` 扩展可用；测试默认连接 `postgres` 数据库，且 `vector` 扩展应位于 `public` schema，避免新测试 schema 报 `type "vector" does not exist`。
- AgentScope 的 `@ToolParam` 注解必须显式提供 `name`，否则会在编译期报错。
- AgentScope 事件消费在 `switch` 中需显式覆盖 `REASONING/TOOL_RESULT/HINT/SUMMARY/AGENT_RESULT/ALL`，避免版本升级后被默认分支静默忽略。
- AgentScope `StreamOptions.eventTypes(...)` 里的 `EventType.ALL` 不是“订阅全部事件”的通配符；聊天主链路若要保留正文流式输出与 thinking/tool 展示，需显式订阅 `REASONING/TOOL_RESULT/HINT/SUMMARY/AGENT_RESULT/ALL`。
- AgentScope 1.0.9 的 DashScope 原生端点自动路由对 `qwen3.5-*` 覆盖不完整，相关模型需走 `knowledge-box.chat.dashscope-compatible.*` 兼容端点策略。
- 已发布 `agent_profile_version.routing_model` 必须指向已启用的 CHAT 模型；启动期会进行强校验，不满足会直接启动失败。
- AgentScope 1.0.9 下，若 `DashScopeChatModel.enableThinking(false)`，不要在对应 `GenerateOptions` 里设置 `thinkingBudget`（即使为 0），否则会抛 `IllegalStateException`。
- AgentScope `Toolkit.registration().group(...)` 不会自动建组；动态注册 Tool/MCP 前必须先 `createToolGroup`，否则会抛 `Tool group ... does not exist`。
- AgentScope 工具执行可能切换线程，`ThreadLocal` 上下文不应作为关键持久化字段（如 `sessionCode`）的唯一来源；落库前需做非空保护或使用显式参数传递。
- 在事务方法里启动异步线程处理审核/生成任务时，需在 `afterCommit` 后再启动，避免子线程读不到尚未提交的数据导致流程丢失。
- “关于” tab 的更新日志来自数据库，不是静态前端文案；新增独立功能时应通过增量变更集补充 `about_release_note` 数据。
- 文档治理链路已切到“审核后发布”：`document_review_request` 未到 `APPROVED` 前，不应直接改写 `knowledge_document` 正式数据。
- 文档分类标签 Agent 依赖外部模型服务；网络/模型失败时应回退兜底分类标签，避免把审核单卡死在 `FAILED`。
- 当 `knowledge-box.storage.provider=oss` 时，`knowledge-box.storage.oss.endpoint/bucket/access-key-id/access-key-secret` 必须显式配置，否则上传链路会直接失败。
- MCP 管理页回显的 Header 为掩码值；后端更新时若收到 `********` 应保留原密文，避免后台编辑非密钥字段时误覆盖密钥。
- 文档标签在落库绑定前需要按大小写无关去重，避免触发 `uk_document_tag_binding_doc_tag` 唯一键冲突。
- 手动 `new PgVectorStore(...).build()` 不会自动触发 Spring 生命周期；若依赖 `initializeSchema`，必须显式调用 `afterPropertiesSet()`。
- 启动期文档初始化导入（bootstrap）必须使用稳定 `importKey` 做幂等去重，避免每次重启重复创建审核单。
- DashScope embedding 接口存在严格单次输入文本条数上限（当前实测上限 10）；向量写入需分批 `add` 且批次不得超过 10，避免审核通过/全量重建时报 `batch size is invalid`。
- 语雀文档迁移/粘贴图片链路若包含大图，需显式配置 `spring.servlet.multipart.max-file-size` 与 `max-request-size`；Spring 默认 1MB 会导致 `/api/admin/documents/paste-image` 抛 `MaxUploadSizeExceededException`。
- 对带唯一键的“绑定表”执行同事务“删除旧绑定+插入新绑定”时，优先使用 JPQL bulk delete（`@Modifying @Query`）或显式 flush，避免 Hibernate 写入顺序触发唯一键冲突。
- 后端已拆分为 `backend-app/service/repository/domain` 多模块；日常编译/测试/启动建议以 `backend/backend-app` 为目标模块并加 `-am` 联动依赖模块。
- 前端健康探测不要直接依赖 `/actuator/health` 聚合状态；邮件等依赖异常会误报 `DOWN`。优先使用业务可用性端点 `/api/public/system/availability`。
- 测试专用 `db.changelog-it.xml` 若追加 `about_release_note` 相关 release note 变更，需先同步建表基线，否则 PostgreSQL 集成测试会在 Liquibase 迁移阶段直接失败。
- AgentScope Hook 事件对象的部分调试字段（如 `generateOptions`）允许为 `null`；记录 trace/debug payload 时不要直接用 `Map.of(...)` 组装可空值。
