# Rule

## Before Starting

- Read `progress.md` first, then read this file.
- If the task is module-specific, also read the matching `docs/progress/<module>/progress.md`.
- Read only the modules relevant to the task; avoid broad, unfocused scanning.
- If the task changes behavior, config, or delivery scope, plan to update `progress.md` before finishing.

## Development Constraints

- Do not hardcode secrets, passwords, JWT keys, SMTP credentials, Redis credentials, database credentials, or real API keys.
- Do not hardcode environment-specific endpoints or origins in Java or frontend source when they should be configurable.
- Prefer explicit configuration in `application-local.yml`, `application-local.yml.example`, environment variables, or documented startup parameters.
- Do not overwrite values already configured by the user in `application-local.yml` unless the user explicitly asks to modify that file.
- `backend/backend-app/src/main/resources/application-local.yml` 视为本地敏感配置文件，必须保持在 `.gitignore` 中，不参与版本追踪。
- 部署用本地真配置（如 `config/knowledge-box.env`、`config/application-prod.yml`）若用于覆盖服务器配置，必须保持在 `.gitignore` 中，不参与版本追踪。
- If shared config structure changes, prefer updating `application.yml`, `application-local.yml.example`, and README; only touch `application-local.yml` when the change is explicitly local-env related and user-approved.
- When changing backend config shape, keep `README.md` and local example config in sync.
- Keep admin-only settings, local-only settings, and shared defaults clearly separated.
- Keep new project rules short and durable; transient task notes belong in `progress.md`, not here.

## Delivery Expectations

- Keep root `progress.md` as the project index/summary; put module detail in `docs/progress/<module>/progress.md`.
- After each meaningful feature, bugfix, or infra change, update the matching module progress; update root `progress.md` only when project-wide focus, module index, or shared notes change.
- After each commit, immediately re-check the matching module progress and bring it in sync with the committed state; if that introduces doc changes, keep committing in the same turn until code and progress are aligned.
- After each independent feature is completed, also add a concise release note to the About tab by inserting data for `about_release_note` through a new additive database changelog/script.
- When creating git commits in this repo, use Chinese commit messages unless the user explicitly asks for another language.
- Git commit messages must use a short subject plus a detailed body; the body should describe as completely as practical the delivered bugfix/optimization/feature, the triggering context or problem, and the solution taken.
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
- 已发布公开聊天入口版本必须保持 `agent_profile_version.agent_type=MAIN`，且 `MAIN` 在库内必须唯一；子 Agent 绑定仅允许 `MAIN/ENTRY/ORCHESTRATOR -> ATOMIC`，且绑定目标固定到具体版本。
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
- 文档 bootstrap 若来源系统会为“同正文不同来源”生成不同 `importKey`，则导入判重不能只看 `importKey`；还需追加正文内容指纹判重，避免跨来源重复内容再次入库。
- DashScope embedding 接口存在严格单次输入文本条数上限（当前实测上限 10）；向量写入需分批 `add` 且批次不得超过 10，避免审核通过/全量重建时报 `batch size is invalid`。
- 语雀文档迁移/粘贴图片链路若包含大图，需显式配置 `spring.servlet.multipart.max-file-size` 与 `max-request-size`；Spring 默认 1MB 会导致 `/api/admin/documents/paste-image` 抛 `MaxUploadSizeExceededException`。
- 对带唯一键的“绑定表”执行同事务“删除旧绑定+插入新绑定”时，优先使用 JPQL bulk delete（`@Modifying @Query`）或显式 flush，避免 Hibernate 写入顺序触发唯一键冲突。
- 后端已拆分为 `backend-app/service/repository/domain` 多模块；日常编译/测试/启动建议以 `backend/backend-app` 为目标模块并加 `-am` 联动依赖模块。
- 文档 bootstrap 的 `seed-file` / `seed-directory` 相对路径是按 JVM 当前工作目录解析，不是按配置文件所在目录解析；本地启动时应使用仓库根目录下可直接命中的路径。
- 语雀 bootstrap seed 若通过 `sourceMarkdownPath` 指向 `tmp/yuque-batch/full-*` 正文，发布包必须保留整棵 `tmp/yuque-batch/`，不能只带 `bootstrap-seeds/`。
- 平铺部署启动脚本若 `source config/knowledge-box.env`，需确保变量已 `export` 给 `java` 子进程；仅 `source` 未导出的 `DB_*`/`KB_*` 会导致 Spring 读取到空配置。
- 若部署依赖 `.env` 中的中文值（如 `KB_MAIL_FROM_PERSONAL`），启动脚本需确保进程 locale 不是 `LANG=C` / `LC_ALL=C`；否则 Java 读取环境变量时很容易把中文解码成 `???`。
- 平铺部署停止脚本不能只发 `TERM` 就立刻返回；需等待旧后端进程实际退出，必要时超时强杀，否则重启时容易撞上旧实例尚未释放端口。
- bootstrap 审核单若卡在 `PROCESSING/CHUNKING`，`importKey` 仍会占用幂等键并阻止后续重启重导；需先恢复任务或清理卡单，再重新执行 bootstrap。
- 前端健康探测不要直接依赖 `/actuator/health` 聚合状态；邮件等依赖异常会误报 `DOWN`。优先使用业务可用性端点 `/api/public/system/availability`。
- 测试专用 `db.changelog-it.xml` 若追加 `about_release_note` 相关 release note 变更，需先同步建表基线，否则 PostgreSQL 集成测试会在 Liquibase 迁移阶段直接失败。
- AgentScope Hook 事件对象的部分调试字段（如 `generateOptions`）允许为 `null`；记录 trace/debug payload 时不要直接用 `Map.of(...)` 组装可空值。
- 管理端删除 Trace 时需保护 `RUNNING` 状态记录；执行中的链路若被删掉，后续 span/event 落库会触发外键或一致性问题。
- Trace 详情里的 `sequenceNo` 是 span/event 共用的全局链路序号，不适合直接当“步骤号”；前端展示应另行按时间线重排步骤编号，并把原始序号明确标成“全局序号”。
- Trace / backend waterfall 这类日志写库若发生在 `@Transactional(readOnly = true)` 服务方法内，开始/结束 span 的持久化必须用独立事务（如 `REQUIRES_NEW`），否则很容易在收尾阶段触发连接已关闭或只读事务写入失败。
- JPA 原生查询在 `JOIN` 多表并返回实体时，`SELECT` 需显式限定到目标表别名（如 `SELECT dc.*`）；直接 `SELECT *` 很容易触发 `NonUniqueDiscoveredSqlAliasException`。
- 带 `timeout` 的工具/任务执行不要把“查询配置 + 实际执行 + 审计落库”整段包进单个长事务；超时分支还需显式 `cancel`/中断后台任务，避免前台已超时但后台继续跑。
- 公开文库目录若底层存在不同 `importKey` 但标题和正文完全相同的公开文档，展示层需按“分类 + 标题 + 正文指纹”去重，否则分类计数和列表会把同一篇文章重复展示。
- Agent 配置导入/导出与启动 bootstrap 统一使用 `profileCode` 作为稳定业务标识；跨环境迁移不要依赖数据库自增 `id`，重复 `profileCode` / `profileName` 启动期默认保留数据库现状。
- 启动期 Agent / Config Bundle bootstrap 的 fail-fast 只能针对真实校验失败；“数据库已存在因此跳过”的幂等消息只能记录为 skip，不能阻断应用启动。
- `published` 只保留给唯一 `MAIN` 公开主入口；用户侧可调试的 Entry Agent 必须使用单独的 `publicDebug` 字段表达，不要复用 `published` 承担两种语义。
- 统一配置 Bundle 导入里的 Skill `packageLocation` 由服务端解析；后台上传的 JSON 不能引用管理员本机路径，需使用服务端可访问的 `file:` / `classpath:` 路径或约定的 `classpath:bootstrap/skills/<code>` 目录。
- 用户主动停止对话回答时，助手消息必须持久化为独立 `CANCELLED` 终态并禁止自动 resume；不要把这种情况混入 `FAILED`。
