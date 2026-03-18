# Admin Observability Progress

## 当前阶段

- 管理端主能力已可支撑日常运营，当前重点是继续提高 Trace 可读性、配置管理一致性和后台操作安全性。

## 已完成

- 管理端已接入模型目录、Agent Profile Version、Hooks、Trace、文档治理，以及动态 Tool/MCP/Skill 绑定管理。
- Agent Profile Version 现已支持 `MAIN / ENTRY / ORCHESTRATOR / ATOMIC` 四种类型，并可按“具体版本”绑定允许调用的原子子 Agent。
- 管理端现已支持新增普通 Agent、删除非主入口 Agent，并对唯一 `MAIN` 主入口提供删除保护。
- 管理端 Agent 配置页现已支持导出全部 Agent JSON，以及“选择本地 JSON -> 导入预览 -> 冲突决策 -> 提交导入”的完整闭环。
- 管理端现已支持统一配置 Bundle 导入导出，可在一份 JSON 中同时维护 Agent、Tool、MCP 与 Skill，并在预览阶段按资源类型展示冲突、现有配置和即将写入的快照。
- 统一配置 Bundle 已升级到 `knowledge-box.config-bundle.v2`，可同时维护 Tool/MCP/Skill 的 `runtimeEnvRequirements` 与 Agent 的 `envVars`。
- Agent Profile Version 绑定页现已支持编辑运行时环境变量，区分 `INLINE / PROCESS_ENV` 两种来源；非 secret inline 值会正常回显，secret inline 值以掩码保留。
- 系统启动期现已支持通过 `knowledge-box.agent.runtime-env-check.*` 做 Agent 运行时环境变量自检，并可按 `fail-fast` 决定是否阻止启动。
- 运行时环境变量解析现已支持从 Spring `Environment` 回退取值，本地可直接在 `application-local.yml` 配置与 `sourceRef` 同名的 key 做联调，无需额外导出 shell 环境变量。
- 系统启动期 bootstrap 现已支持统一配置 Bundle schema；Skill 可按约定从 `classpath:bootstrap/skills/<code>` 或显式 `packageLocation` 目录自动打包并上传到 OSS。
- 系统启动期已支持通过 `knowledge-box.agent.bootstrap.*` 从外置 JSON seed file / seed directory 自动创建缺失 Agent，并在重复 `profileCode` / `profileName` 时保留数据库现状并记录告警。
- Trace 已支持列表、详情、删除、时间线、瀑布图与通俗解读视图。
- 管理端公共布局已修复为内容区独立滚动，`知识文档` 与 `文档审核` 页面在关闭窗口级滚动后仍可正常使用。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖 Trace 管理页、后台布局滚动修复和相关运营页面。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖 Agent 配置页导出 JSON、导入预览 Modal、冲突动作选择与提交调用链。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖统一配置 Bundle 的导入导出入口、通用资源预览表格、运行时环境变量 JSON 编辑与冲突处理。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `package` 可通过，已覆盖 Trace、Agent 配置与后台管理接口。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentProfileBindingServiceTests,AgentProfileVersionPolicyServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests,PublishedProfileRoutingModelValidatorTests test` 可通过，已覆盖 Agent 类型约束、子 Agent 绑定与运行时装配。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,AgentProfileVersionPolicyServiceTests,PublishedProfileRoutingModelValidatorTests test` 可通过，已覆盖 Agent 创建删除、`MAIN` 唯一性和主入口校验。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentConfigAdminServiceTests,AdminCommandServiceTests,AgentProfileVersionPolicyServiceTests test` 可通过，已覆盖 Agent JSON 导入预览、覆盖提交与启动期跳过策略。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ConfigBundleAdminServiceTests,AgentConfigAdminServiceTests,AgentCapabilityAssemblyServiceTests,AgentProfileBindingServiceTests,ChatOrchestratorTests test` 可通过，已覆盖统一配置 Bundle v2、运行时环境变量回显/导出以及 web-search 子 Agent 装配。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentRuntimeEnvStartupCheckRunnerTests,AgentProfileBindingServiceTests,ConfigBundleAdminServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests test` 可通过，已覆盖启动期 env 自检、缺失宿主环境变量告警与 fail-fast 启动保护。

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
