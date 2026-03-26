# Progress

## 当前阶段

- 主线能力已成型，当前重点是继续收口文档治理、管理端运营能力，以及聊天体验细节。
- 进度文档已改为“根总览 + 模块子进度”结构；根文档只保留项目级导航、全局重点和共享注意点。

## 当前重点

- 文档治理闭环继续收口：审核策略、失败可观测性、重复治理和导入运维仍是近期主线。
- 用户侧知识入库工作台已接上同步草稿 + 大 PDF 异步任务双链路：当前支持上传 Markdown / 小型文本型 PDF 或直接粘贴内容生成草稿，也支持大体量文本型 PDF 异步拆解为多个待审核文档；任务中心入口、审核跳转按钮、分类临时新建、草稿链接高亮，以及非运行中任务删除与源文件查看已补齐，后续重点转向真实 OSS、模型与审核运营联调。
- 用户体验继续打磨：聊天流式细节、公开文库阅读体验和用户工具扩展仍有持续迭代空间。
- 公开信息页已扩展：当前新增公开“关于作者”个人主页，原“关于”已改为“更新日志”并迁到 `/log`，后续重点转向真实内容填充、移动端排版与 SEO/对外展示细节联调。
- 聊天停止链路已补齐：当前支持用户主动停止回答并以 `CANCELLED` 持久化，后续重点转向真实模型调用下的中断时延观察。
- 用户侧多入口调试已补齐：当前支持独立 `Agent 调试` 工作区，后续重点转向真实环境下不同 Entry Agent 的联调与可用性反馈。
- 真实环境联调继续补齐：Redis、邮件、OSS、模型配置与部署链路仍需持续验证。
- Agent 运行时配置继续完善：当前已补齐 Agent 级环境变量、外网搜索子 Agent 与配置 Bundle v2，后续重点转向真实联网场景联调。
- Agent 启动导入链路已补齐：web-search 相关 bundle 现可在本地与远程部署时自动导入，后续重点转向真实环境下的首启验证与幂等性观察。
- Agent 提示词配置继续细化：当前已收口为 Agent 版本基础 `systemPrompt`；MAIN 的知识库查询策略改由 `systemPrompt` + Tool 绑定共同表达，后续重点转向真实多入口 Agent 的运营配置沉淀。

## 模块索引

- [聊天与用户对话](docs/progress/chat/progress.md)：聊天主链路、会话、SSE、引用与对话页交互状态。
- [文档治理](docs/progress/document-governance/progress.md)：导入、审核、标签分类、专栏、索引和重复治理状态。
- [公开文库](docs/progress/public-library/progress.md)：`/articles`、公开文档详情、专栏阅读与公开目录体验状态。
- [工具平台](docs/progress/tool-platform/progress.md)：用户工具目录、执行链路、schema 驱动配置与工具模板状态。
- [管理端与可观测性](docs/progress/admin-observability/progress.md)：模型目录、Agent Profile、Hooks、Trace、后台布局与运营视图状态。
- [部署与运行时](docs/progress/deploy-runtime/progress.md)：多环境构建、发布脚本、远程平铺部署和本地运行链路状态。

## 最近全局验证

- `npm --prefix frontend run build` 已多轮通过，覆盖聊天、公开文库、工具平台、文档审核与 Trace 管理页等近期主线改动。
- `npm --prefix frontend run build` 已再次通过，覆盖用户侧知识入库页、工作区导航与确认表单接线。
- `mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `package` 已通过，覆盖聊天编排、文档治理、工具平台、Trace 与公开文档接口。
- `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeIngestionSourceToolTests,KnowledgeIngestionServiceTests test` 已通过，覆盖知识入库 source tool、草稿分析状态流转与确认入审核单链路。
- `mvn -q -pl backend/backend-app -am -DskipTests compile` 已再次通过，覆盖 Agent 版本级 prompt 模板字段、知识库 Tool 绑定装配和 Liquibase 迁移。
- `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentCapabilityAssemblyServiceTests,AgentProfileBindingServiceTests,ConfigBundleAdminServiceTests,AgentConfigAdminServiceTests,ChatOrchestratorTests test` 已通过，覆盖 Agent 运行时环境变量、配置 Bundle v2 与 web-search 子 Agent 装配回归。
- `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentRuntimeEnvStartupCheckRunnerTests,AgentProfileBindingServiceTests,ConfigBundleAdminServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests test` 已通过，覆盖 Agent 运行时环境变量启动自检、必填 requirement 校验与 fail-fast 启动保护。
- Agent 运行时 `PROCESS_ENV` 现已支持在缺少宿主环境变量时回退读取 Spring `Environment`，因此可直接从 `application-local.yml` 读取 `KB_TAVILY_API_KEY` 这类配置做本地启动联调。
- `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,AgentProfileVersionPolicyServiceTests,PublishedProfileRoutingModelValidatorTests test` 已通过，覆盖 Agent 创建删除、`MAIN` 唯一性与公开入口校验。
- `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,AgentProfileVersionPolicyServiceTests,ChatOrchestratorTests test` 已通过，覆盖 `publicDebug` 调试入口约束、按入口停止回答与删除 Agent 时的聊天/trace 清理。
- `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests,AgentConfigAdminServiceTests,ConfigBundleAdminServiceTests test` 已通过，覆盖 Agent 版本级 `systemPrompt`、知识库 Tool 绑定判定、无知识库绑定入口跳过路由/检索，以及配置导入导出回归。
- `mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `npm --prefix frontend run build` 已通过，覆盖知识库分类路由移除、Agent 配置页去除路由模型显式配置，以及示例 bundle/seed 同步收口。
- `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ChatOrchestratorTests,AdminCommandServiceTests,PublishedProfileRoutingModelValidatorTests,AgentConfigAdminServiceTests,ConfigBundleAdminServiceTests test` 已通过，覆盖“绑定 KB tool 即启用 `searchKnowledgeBase`”、发布入口改校验 `chatModel`，以及 Agent/Bundle 导入导出对历史 `routingModel` 字段的兼容回填。
- `npm --prefix frontend run build` 与 `mvn -q -pl backend/backend-app -am -DskipTests compile` 已通过，覆盖管理端移除知识库模板字段、MAIN 默认 `systemPrompt` 收口，以及 Agent/Bundle/seed 示例同步。
- `mvn -q -pl backend/backend-app -am -DskipTests compile`、`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeIngestionServiceTests,KnowledgeIngestionTaskServiceTests test` 与 `npm --prefix frontend run build` 已通过，覆盖大 PDF 自动分流、异步任务拆解、取消保留已产出审核单，以及 `/ingest/tasks/:taskId` 任务页编译回归。
- `npm --prefix frontend run build` 已通过，覆盖知识入库任务中心 `/ingest/tasks`、确认后前往审核页按钮、分类临时新建，以及 Markdown 草稿链接高亮回归。
- `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeIngestionTaskServiceTests test` 与 `npm --prefix frontend run build` 已通过，覆盖大 PDF 文本提取逐页进度更新、当前页片段预览、任务页摘要展示与更高频轮询回归。
- `mvn -q -pl backend/backend-app -am -DskipTests compile`、`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeIngestionTaskServiceTests test` 与 `npm --prefix frontend run build` 已通过，覆盖知识入库任务删除接口、源文件删除抽象、任务中心/详情页删除入口，以及已完成任务查看源文件回归。
- `mvn -q -pl backend/backend-app -am -DskipTests compile`、`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AuthorProfileAdminServiceTests test` 与 `npm --prefix frontend run build` 已通过，覆盖作者主页表结构、公开 `/author` / `/log` 路由、后台作者资料编辑与照片上传回归。
- 本地 `java -jar backend/backend-app/target/knowledge-box-backend-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=18081` 已验证可启动，`/api/public/system/availability` 返回 `UP`。
- 当前沙箱下全量 PostgreSQL 集成测试仍可能因本机数据库连接受限失败；这属于环境限制，不是最近文档拆分导致的行为回归。

## 全局注意点

- `application-local.yml` 仅在 `local` profile 生效，且视为用户本地敏感配置，不要覆盖。
- 模块任务先读根 `progress.md`，再读对应 `docs/progress/<module>/progress.md`；根文档只保留索引与共享状态。
- “关于”tab 的更新日志来自数据库；独立功能完成后要补增量 changelog 往 `about_release_note` 写数据。
- 公开“关于作者”页当前使用结构化表单数据 + Markdown 渲染，不直接托管整页 HTML；管理员编辑入口在 `/admin/author-profile`，用户侧公开入口为 `/author`。
- 用户侧知识入库当前只支持文本型 PDF，不做 OCR；上传源文件会保留，小文件走单草稿确认链路，大文件则进入异步拆解任务并保留已生成的待审核文档。
- Git 提交默认使用中文“简短标题 + 详细正文”；正文尽量完整说明对应 bug/优化/功能、问题背景和解决办法。
- 每次提交后都要回到对应模块 progress 检查并补齐当前进度；若进度因此变更，也要继续提交这些文档更新。
- 发布包中的语雀 bootstrap seed 若通过 `sourceMarkdownPath` 指向 `tmp/yuque-batch/full-*` 正文，不能只打包 `bootstrap-seeds/`，必须保留整棵 `tmp/yuque-batch/`。
