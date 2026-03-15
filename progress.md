# Progress

## 当前阶段

- 主线能力已基本成型，当前重点是继续收口文档治理闭环、管理端运营能力，以及聊天体验细节。

## 核心进度

- 用户侧已具备登录、知识库问答、会话持久化、SSE 流式输出、历史恢复、删除会话与“关于”tab。
- 后端已建立 Spring Boot + Liquibase + PostgreSQL/pgvector 基础工程，并已拆分为 `backend-app/service/repository/domain` 四个 Maven 子模块。
- 大模型主链路已切换到 AgentScope Java ReActAgent，并接入 tool calling、查询路由、reasoning/tool/citation 展示与 trace。
- 聊天主链路已修正 AgentScope 流式事件订阅，最终回答正文恢复走 `SUMMARY -> delta/fullContent` 主输出区，thinking/tool 仅留在小字摘要区。
- 聊天主链路已切换为默认“前置知识库检索”模式：每次问答会先做一次公开知识片段检索，再把命中内容注入回答上下文；若未命中足够证据，仍允许通用回答，但会明确提示知识库未提供支持证据。
- 知识检索已增强 query variant 召回：对 `讲一下mcp是什么` 这类中英混合缩写问题，会自动提取缩写关键词并融合向量/全文/内存检索结果，且在检索阶段就过滤 `AGENT_ONLY` 文档，避免无效引用进入 prompt 和 citations。
- 用户侧聊天页已移除右侧“关联文件”侧栏；引用来源改为挂在每条 Assistant 回答下方，并支持新开独立窗口查看公开文档全文，不覆盖当前对话窗口。
- 用户侧聊天引用已改为按文档聚合展示；同一回答命中同一文档的多段内容时，会合并成一条引用并汇总章节/摘要，避免重复出现同一文档。
- 用户侧文档详情新窗口已改为按当前浏览器尺寸自适应打开，并复用管理端 Markdown 预览渲染与图片缩略规则，避免正文横向溢出和超大图片撑破窗口。
- 管理端已接入模型目录、Agent Profile Version、Hooks、Trace、文档治理与动态 Tool/MCP/Skill 绑定管理。
- 管理端 Trace 已升级为管理员专属的 Agent 调用链日志系统：后端按 `trace/span/event` 持久化 prompt 注入、thinking/summary、工具调用、最终回复与耗时，前端支持列表筛选与详情时间线查看。
- 管理端 Trace 现支持删除单条已结束的执行链路；列表页和详情页都可删除，并会级联清理对应的 span/event 明细。
- 管理端 Trace 详情页已把“页面步骤号”与“后端全局序号”分开展示，并补齐阶段说明、输入摘要、输出摘要与事件摘要，便于管理员快速读懂链路。
- 管理端 Trace 详情页已升级为双层分析视图：`Agent 时间线` 严格按时间顺序展示请求/Prompt/工具/最终回复等语义步骤，`后端调用瀑布` 展示关键服务调用的父子关系、开始偏移与耗时条，同时保留 `原始日志` 视图做深度排障。
- 聊天工具执行链路已改为通过 AgentScope `ToolExecutionContext` 显式注入 trace/runtime 上下文，不再依赖 `ThreadLocal sessionCode` 反查，避免工具切线程时 trace/backend waterfall 丢 span 或串链。
- 管理端 `Agent 时间线` 已修正事件状态语义：`agent.call.start` 等瞬时事件不再显示误导性的 `RUNNING/COMPLETED` 状态标签，仅在异常事件上显示 `FAILED`。
- 管理端 Trace 详情页现支持真正的分层展示：`Agent 时间线` 按请求/span/event 构造成可折叠树，工具调用与路由步骤会在父阶段下缩进展开；`后端调用瀑布` 也按 `parentCallId` 递归缩进，保持同层一致的视觉层级。
- 管理端 Trace 详情页已新增 `通俗解读` 视图：后端对 Agent 事件与后端调用做统一语义映射，前端可切换查看 `Agent 过程解读` 与 `后端调用解读`，用更直白的标题/输入/输出解释整条链路。
- 文档治理链路已落地：文档上传、审核流、分类标签、索引重建、Markdown 预览/编辑、图片转存、向量写入与 bootstrap 初始化导入。
- 管理端文档审核队列已支持批量审核通过：可勾选当前页多条 `PENDING_REVIEW` 记录一次性发布，后端复用单条审核发布链路批量写入分类、标签、资产与向量索引。
- 管理端文档审核页已修复批量选择同步导致的 React 死循环；当前页待审核集合未变化时不再重复 `setState`，避免进入页面即触发 `Maximum update depth exceeded`。
- 初始化数据已补充前台可登录管理员账号 `admin@example.com`，可直接用 `admin123` 登录用户侧首页。
- 本地 profile 的文档 bootstrap 导入路径已修正为仓库根目录可直接解析的 `backend/bootstrap/document-seed.json` 与 `tmp/yuque-batch/bootstrap-seeds`，启动时可自动扫描并导入语雀批量 seed。
- 前端已补齐全局后端可用性提示（改为右侧悬浮卡片，不阻断页面渲染）、底部备案 footer（工信部链接）和文档审核更新时间秒级展示。
- 前端后端可用性探测已改为“页面加载时单次探测”；探测失败仅提示一次，不再自动轮询重试，避免浏览器资源被持续占用。
- 后端新增独立可用性接口 `/api/public/system/availability`，用于前端健康探测，不再依赖 actuator 聚合健康状态。
- 已新增项目级 `yuque-openapi-guide` skill，并补充可执行脚本 `scripts/yuque_api.py`，用于读取个人语雀知识库、文档、目录、搜索与历史版本。
- 已新增 `scripts/README.md`，用于说明“后端运行中”如何用 `yuque_kb_migrate.py` 动态导入语雀文档（单篇 + 批量）到审核流。

## 关键注意点

- `application-local.yml` 仅在 `local` profile 生效，且视为用户本地敏感配置，不要覆盖。
- “关于”tab 的更新日志来自数据库；独立功能完成后要补增量 changelog 往 `about_release_note` 写数据。
- AgentScope 相关高频坑仍需注意：
  - `@ToolParam` 必须显式写 `name`
  - 事件分支需显式覆盖 `REASONING/TOOL_RESULT/HINT/SUMMARY/AGENT_RESULT/ALL`
  - `DashScopeChatModel.enableThinking(false)` 时不要再设置 `thinkingBudget`
  - 动态注册 Tool/MCP 前先建 tool group
  - Hook 事件里的 `generateOptions` 等字段可能为 `null`，trace/debug payload 不要直接用 `Map.of(...)`
- 文档治理相关高频坑仍需注意：
  - 审核/生成异步线程要在 `afterCommit` 后启动
  - 标签绑定写入前要去重，删除旧绑定优先 bulk delete 或显式 flush
  - DashScope embedding 单批上限按 `10` 控制
  - bootstrap 导入必须依赖稳定 `importKey` 做幂等
- Git 提交信息默认使用中文。

## 最近验证

- 前端验证：`npm --prefix frontend run build` 通过（包含后端异常提示条、备案 footer、审核时间格式化改动，以及健康探测失败不自动重试修复）。
- 前端验证：`npm --prefix frontend run build` 通过（含登录页技术性 SMTP 提示移除与验证码失败文案收口）。
- 前端验证：`npm --prefix frontend run build` 通过（含管理员 trace 列表页、详情页、筛选和时间线展示）。
- 前端验证：`npm --prefix frontend run build` 通过（含公共对话页最小高度占满视口与备案 footer 固定在页面底部的布局修复）。
- 前端验证：`npm --prefix frontend run build` 通过（含管理员 trace 详情页按步骤折叠卡片展示时间线，支持快速定位每个 span / orphan event）。
- 前端验证：`npm --prefix frontend run build` 通过（含管理员 trace 列表页 / 详情页删除单条 trace 的操作入口）。
- 前端验证：`npm --prefix frontend run build` 通过（含 trace 详情页步骤号/全局序号分离展示，以及阶段/输入/输出/事件摘要优化）。
- 前端验证：`npm --prefix frontend run build` 通过（含 trace 详情双层分析视图、RUNNING trace 禁删保护、时间线摘要回退与后端瀑布比例修正）。
- 前端验证：`npm --prefix frontend run build` 通过（含 trace 详情页 Agent 时间线树形分层与后端瀑布统一层级缩进改造）。
- 前端验证：`npm --prefix frontend run build` 通过（含 Trace 详情页 `专业视图 / 通俗解读` 切换，以及可读解释树/瀑布渲染）。
- 后端编译验证：`mvn -q -pl backend/backend-app -am -DskipTests compile` 通过。
- 后端定向回归：`mvn -q -pl backend/backend-app -am -Dtest=ChatOrchestratorTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含 `MODEL_ROUTED` 与 `ALWAYS_PRE_RETRIEVE` 两种检索触发模式的 fallback 语义回归）。
- 后端集成验证：`mvn -q -pl backend/backend-app -am -Dtest=KnowledgeBoxPostgresIntegrationTests,KnowledgeBoxProductionLiquibaseIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（需放开本机 PostgreSQL 访问；含 MCP 缩写查询引用回归与 `db.changelog-031-chat-pre-retrieval-release-note.xml` 生产 Liquibase 回归）。
- 后端编译验证：`mvn -q -pl backend/backend-app -am -DskipTests compile` 通过（含 trace readable DTO、后端语义映射与详情接口扩展）。
- 后端打包验证：`mvn -q -pl backend/backend-app -am -DskipTests package` 通过。
- 本地启动验证：`java -jar backend/backend-app/target/knowledge-box-backend-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=18081` 通过，启动日志显示 `[DOCUMENT-BOOTSTRAP]` 已扫描 `backend/bootstrap/document-seed.json` 与 `tmp/yuque-batch/bootstrap-seeds/yuque-batch.seed.json`，总计 `created=0 skipped=91 failed=0`（当前库内已存在对应 `importKey`，因此全部按幂等跳过）。
- 本地数据库验证：`psql -d knowledge_box ...` 查询通过，`document_review_request` 中 `importKey` 记录数为 `91`，示例 `yuque:70111390:243374419` 已存在 1 条；HTTP 可用性 `curl -s http://127.0.0.1:18081/api/public/system/availability` 返回 `{\"status\":\"UP\",\"reachable\":true,...}`。
- 前端验证：`npm --prefix frontend run build` 通过（含文档审核队列多选、当前页全选/清空与批量审核通过入口）。
- 前端验证：`npm --prefix frontend run build` 通过（修复文档审核页批量选择同步引起的 `Maximum update depth exceeded` 死循环）。
- 前端验证：`npm --prefix frontend run build` 通过（含聊天页移除右侧关联文件栏、回答内联引用展示与用户侧文档详情页）。
- 前端验证：`npm --prefix frontend run build` 通过（含回答引用按文档聚合展示，历史消息与流式消息都不再重复显示同一文档）。
- 前端验证：`npm --prefix frontend run build` 通过（含用户侧文档详情页自适应窗口尺寸、Markdown 正文横向约束与图片缩略显示）。
- 后端编译验证：`mvn -q -pl backend/backend-app -am -DskipTests compile` 通过（含批量审核通过接口、批量请求/响应 DTO 与审核服务复用单条发布链路）。
- 后端编译验证：`mvn -q -pl backend/backend-app -am -DskipTests compile` 通过（含用户侧公开文档详情接口与聊天引用新窗口查看链路）。
- 后端定向回归：`mvn -q -pl backend/backend-app -am -Dtest=ChatOrchestratorTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含同一文档多段命中时 citation 按文档聚合回归）。
- 后端集成验证：`mvn -q -pl backend/backend-app -am -Dtest=KnowledgeBoxProductionLiquibaseIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含 `db.changelog-032-chat-inline-citation-release-note.xml` 生产 Liquibase 迁移回归）。
- 后端集成验证：`mvn -q -pl backend/backend-app -am -Dtest=KnowledgeBoxPostgresIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含批量审核通过接口成功发布两条待审核文档；测试日志中仍有文档审核分类 Agent 的 DashScope 401 背景噪声，但未影响用例通过）。
- 后端 Liquibase 验证：`mvn -q -pl backend/backend-app -am -Dtest=KnowledgeBoxProductionLiquibaseIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含 `db.changelog-030-document-review-batch-approve-release-note.xml` 关于页更新日志变更集）。
- 后端编译验证：`mvn -q -pl backend/backend-app -am -DskipTests compile` 通过（含 Agent execution trace 实体、服务、管理端查询接口与清理任务）。
- 后端编译验证：`mvn -q -pl backend/backend-app -am -DskipTests compile` 通过（含双层 trace 视图、后端瀑布 span 落库，以及 ToolExecutionContext 显式上下文修正）。
- 后端编译验证：`mvn -q -pl backend/backend-app -am -DskipTests compile` 通过（含 Agent 时间线事件状态语义修正，避免把 `agent.call.start` 误显示为 `RUNNING`）。
- 后端单测抽样：`mvn -q -pl backend/backend-app -am -Dtest=AgentCapabilityAssemblyServiceTests -Dsurefire.failIfNoSpecifiedTests=false test` 与 `AgentProfileBindingServiceTests` 通过。
- 一键回归脚本验证：`bash scripts/quick-regression.sh` 通过（后端编译 + 关键单测 + 前端构建）。
- 后端集成验证：`mvn -q -pl backend/backend-app -am -Dtest=KnowledgeBoxPostgresIntegrationTests,KnowledgeBoxProductionLiquibaseIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含 `admin@example.com` 初始化账号与前台密码登录回归）。
- 后端集成验证：`mvn -q -pl backend/backend-app -am -Dtest=KnowledgeBoxPostgresIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（需放开沙箱访问本机 PostgreSQL；含 trace 详情新增 `readableAgentTimeline/readableBackendTimeline` 与 IT changelog 回归，测试日志中仍有文档审核后台线程 DashScope 401 背景噪声）。
- 后端全量测试：`mvn -q -pl backend/backend-app -am test` 通过（本机 PostgreSQL + pgvector 已就绪，含 `KnowledgeBoxPostgresIntegrationTests` 与 `KnowledgeBoxProductionLiquibaseIntegrationTests`）。
- 后端定向回归：`mvn -q -pl backend/backend-app -am -Dtest=ChatOrchestratorTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含流式事件订阅集合与 AgentScope 事件消费回归）。
- 后端集成验证：`mvn -q -pl backend/backend-app -am -Dtest=KnowledgeBoxPostgresIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含 Agent execution trace 落库、管理端 trace 列表/详情接口与 IT Liquibase 新增 changelog 回归）。
- 后端集成验证：`mvn -q -pl backend/backend-app -am -Dtest=KnowledgeBoxPostgresIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含管理员删除单条 trace 后 trace/span/event 级联清理与详情接口 404 回归）。
- 后端定向回归：`mvn -q -pl backend/backend-app -am -Dtest=AgentExecutionTraceHookTests,ChatOrchestratorTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（修复 AgentExecutionTraceHook 对 `null generateOptions` 使用 `Map.of(...)` 触发的聊天 NPE）。
- 后端定向回归：`mvn -q -pl backend/backend-app -am -Dtest=KnowledgeBoxPostgresIntegrationTests,AgentExecutionTraceHookTests,ChatOrchestratorTests,KnowledgeBaseSearchToolTests,AgentCapabilityAssemblyServiceTests -Dsurefire.failIfNoSpecifiedTests=false test` 已完成通过（含双层 trace 视图、后端瀑布 span、ToolExecutionContext 显式 trace/runtime 注入）；测试日志中仍会看到文档审核分类 Agent 的 DashScope 401 背景噪声，但未导致本轮目标用例失败。
- 语雀 skill 脚本验证：`python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py --help` 与语法编译检查通过。
- 导入脚本命令校验：`python3 scripts/yuque_kb_migrate.py --help` 与三个子命令 `--help` 均可正常执行。

## 待继续推进

- 文档审核策略继续细化：批量审核、权限颗粒度、审核原因规范化、可观测性增强。
- 聊天体验继续打磨：流式颗粒度、未完成会话恢复、Markdown/代码块渲染细节。
- 聊天体验继续打磨：流式颗粒度、未完成会话恢复、Markdown/代码块渲染细节，以及前端对异常/未知流事件的兜底展示。
- Redis、邮件发送、OSS 与真实模型配置的本地联调继续补齐。
