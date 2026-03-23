# Admin Observability Progress

## 当前阶段

- 管理端主能力已可支撑日常运营，当前重点是继续提高 Trace 可读性、配置管理一致性和后台操作安全性。

## 已完成

- 管理端已接入模型目录、Agent Profile Version、Hooks、Trace、文档治理，以及动态 Tool/MCP/Skill 绑定管理。
- Agent Profile Version 现已支持 `MAIN / ENTRY / ORCHESTRATOR / ATOMIC` 四种类型，并可按“具体版本”绑定允许调用的原子子 Agent。
- 管理端现已支持新增普通 Agent、删除非主入口 Agent，并对唯一 `MAIN` 主入口提供删除保护。
- Agent 版本现已新增 `publicDebug` 开关；只有 `ENTRY + PUBLISHED + publicDebug=true` 的版本会暴露给用户侧 `Agent 调试` 页。
- 删除非 MAIN Agent 时，系统现在会同步清理该 `profileCode` 关联的用户调试会话与 trace 数据；若仍有 `RUNNING` trace，则会拒绝删除以保护链路一致性。
- 管理端 Agent 配置页现已支持导出全部 Agent JSON，以及“选择本地 JSON -> 导入预览 -> 冲突决策 -> 提交导入”的完整闭环。
- 管理端现已支持统一配置 Bundle 导入导出，可在一份 JSON 中同时维护 Agent、Tool、MCP 与 Skill，并在预览阶段按资源类型展示冲突、现有配置和即将写入的快照。
- 统一配置 Bundle 已升级到 `knowledge-box.config-bundle.v2`，可同时维护 Tool/MCP/Skill 的 `runtimeEnvRequirements` 与 Agent 的 `envVars`。
- Agent Profile Version 绑定页现已支持编辑运行时环境变量，区分 `INLINE / PROCESS_ENV` 两种来源；非 secret inline 值会正常回显，secret inline 值以掩码保留。
- 管理端 Agent 配置页当前只保留基础 `systemPrompt`；知识库是否必须先检索改由 MAIN 的 `systemPrompt` + Tool 绑定表达，Agent/Bundle 导入导出与 bootstrap 示例也同步只保留这一字段。
- 管理端 Agent 配置现已去掉独立 `routingModel` 显式配置；发布入口改为直接校验 `chatModel`，Agent/Bundle 导入仍兼容历史 JSON 中的 `routingModel` 字段，并在保存时自动回填为 `chatModel`。
- 系统启动期现已支持通过 `knowledge-box.agent.runtime-env-check.*` 做 Agent 运行时环境变量自检，并可按 `fail-fast` 决定是否阻止启动。
- 运行时环境变量解析现已支持从 Spring `Environment` 回退取值，本地可直接在 `application-local.yml` 配置与 `sourceRef` 同名的 key 做联调，无需额外导出 shell 环境变量。
- 系统启动期 bootstrap 现已支持统一配置 Bundle schema；Skill 可按约定从 `classpath:bootstrap/skills/<code>` 或显式 `packageLocation` 目录自动打包并上传到 OSS。
- 系统启动期已支持通过 `knowledge-box.agent.bootstrap.*` 从外置 JSON seed file / seed directory 自动创建缺失 Agent，并在重复 `profileCode` / `profileName` 时保留数据库现状并记录告警。
- 启动期统一配置 Bundle / Agent bootstrap 的 fail-fast 逻辑已修复为“仅真实校验失败才阻断启动”；对数据库里已存在资源的幂等跳过现在只记消息与计数，不再导致应用启动失败。
- 默认 `config-bundle.web-search.json` 现已额外提供一个 `general-entry-agent`：它以 `ENTRY + PUBLISHED + publicDebug=true` 方式公开给用户侧调试页，并默认绑定 `web-search-agent` 作为子 Agent。
- Trace 已支持列表、详情、删除、时间线、瀑布图与通俗解读视图。
- 管理端公共布局已修复为内容区独立滚动，`知识文档` 与 `文档审核` 页面在关闭窗口级滚动后仍可正常使用。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖 Trace 管理页、后台布局滚动修复和相关运营页面。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖 Agent 配置页导出 JSON、导入预览 Modal、冲突动作选择与提交调用链。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖统一配置 Bundle 的导入导出入口、通用资源预览表格、运行时环境变量 JSON 编辑与冲突处理。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖 Agent 管理页的 `publicDebug` 开关、调试入口标签与新的用户侧 `Agent 调试` tab。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `package` 可通过，已覆盖 Trace、Agent 配置与后台管理接口。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentProfileBindingServiceTests,AgentProfileVersionPolicyServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests,PublishedProfileRoutingModelValidatorTests test` 可通过，已覆盖 Agent 类型约束、子 Agent 绑定与运行时装配。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,AgentProfileVersionPolicyServiceTests,PublishedProfileRoutingModelValidatorTests test` 可通过，已覆盖 Agent 创建删除、`MAIN` 唯一性和主入口校验。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentConfigAdminServiceTests,AdminCommandServiceTests,AgentProfileVersionPolicyServiceTests test` 可通过，已覆盖 Agent JSON 导入预览、覆盖提交与启动期跳过策略。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ConfigBundleAdminServiceTests,AgentConfigAdminServiceTests,AgentCapabilityAssemblyServiceTests,AgentProfileBindingServiceTests,ChatOrchestratorTests test` 可通过，已覆盖统一配置 Bundle v2、运行时环境变量回显/导出以及 web-search 子 Agent 装配。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ConfigBundleAdminServiceTests,AgentConfigAdminServiceTests test` 可通过，已覆盖启动期 bootstrap 在 `failFast=true` 时对重复资源的幂等跳过策略回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentRuntimeEnvStartupCheckRunnerTests,AgentProfileBindingServiceTests,ConfigBundleAdminServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests test` 可通过，已覆盖启动期 env 自检、缺失宿主环境变量告警与 fail-fast 启动保护。
- 后端：`java -jar backend/backend-app/target/knowledge-box-backend-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=18081` 已验证不再触发 bootstrap skip 异常；当前沙箱下仅因 PostgreSQL 连接受限而停止，说明启动链路已越过本次回归点。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,AgentProfileVersionPolicyServiceTests,ChatOrchestratorTests test` 可通过，已覆盖 `publicDebug` 约束、删除 Agent 时的聊天/trace 清理与调试会话停止链路。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,AgentConfigAdminServiceTests,ConfigBundleAdminServiceTests,ChatOrchestratorTests test` 可通过，已覆盖 Agent 版本级 `systemPrompt`、配置 Bundle 导入导出以及知识库 Tool 绑定启用策略。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖管理端 Agent 配置页移除知识库模板表单后的创建/编辑回填链路。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖 Agent 配置页移除“路由模型”字段后的类型、表格与创建/编辑表单回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,PublishedProfileRoutingModelValidatorTests,AgentConfigAdminServiceTests,ConfigBundleAdminServiceTests test` 可通过，已覆盖发布入口改校验 `chatModel`、配置导入导出去除显式 `routingModel`，以及历史 bundle 兼容解析回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeBoxPostgresIntegrationTests#shouldLoadModelCatalogAndUpdateProfileVersion test` 可通过，已覆盖管理端更新 Agent 版本接口在移除显式 `routingModel` 后仍需携带 `agentType` 的当前请求契约。

## 待继续推进

- 继续提升 Trace 时间线与后台执行信息的可读性，降低排障门槛。
- 收口后台运营页之间的状态一致性、删除保护与异常提示。
- 持续补齐模型配置、Agent Profile 和动态集成管理的运维体验。

## 关键注意点

- 管理端删除 Trace 时需继续保护 `RUNNING` 状态记录，避免执行中的链路被删掉后出现外键或一致性问题。
- Trace 详情里的 `sequenceNo` 是全局序号，不适合直接当“步骤号”。
- Hook 事件里的调试字段可能为 `null`，trace/debug payload 组装时不要直接用 `Map.of(...)`。
- 若日志写库发生在 `@Transactional(readOnly = true)` 服务方法内，开始/结束 span 的持久化必须使用独立事务。
- 已发布公开入口版本必须保持为唯一 `MAIN`；子 Agent 绑定仅允许 `MAIN/ENTRY/ORCHESTRATOR -> ATOMIC`，且绑定目标固定到具体版本。
- Agent 配置导入/导出与启动 bootstrap 统一使用 `profileCode` 作为稳定业务标识；跨环境迁移不要依赖数据库自增 `id`。
- 统一配置 Bundle 的 Skill 导入是服务端读取 `packageLocation` 对应目录并自动打包；管理端上传的 JSON 若引用本机客户端路径不会生效，需使用服务端可访问的 `file:` / `classpath:` 路径或约定目录。
- 运行时环境变量的 secret inline 值在后台回显为 `********`，提交时若仍传掩码会保留原密文；非 secret inline 值允许明文导出，避免 Bundle 导出后丢配置。
- `published` 继续只表示唯一 MAIN 公开主入口；用户侧可调试入口必须使用独立的 `publicDebug` 字段表达，避免把主入口语义和调试暴露语义混在一起。
