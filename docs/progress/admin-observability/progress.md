# Admin Observability Progress

## 当前阶段

- 管理端主能力已可支撑日常运营，当前重点是继续提高 Trace 可读性、配置管理一致性和后台操作安全性。

## 已完成

- 管理端已接入模型目录、Agent Profile Version、Hooks、Trace、文档治理，以及动态 Tool/MCP/Skill 绑定管理。
- Agent Profile Version 现已支持 `MAIN / ENTRY / ORCHESTRATOR / ATOMIC` 四种类型，并可按“具体版本”绑定允许调用的原子子 Agent。
- 管理端现已支持新增普通 Agent、删除非主入口 Agent，并对唯一 `MAIN` 主入口提供删除保护。
- Trace 已支持列表、详情、删除、时间线、瀑布图与通俗解读视图。
- 管理端公共布局已修复为内容区独立滚动，`知识文档` 与 `文档审核` 页面在关闭窗口级滚动后仍可正常使用。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖 Trace 管理页、后台布局滚动修复和相关运营页面。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `package` 可通过，已覆盖 Trace、Agent 配置与后台管理接口。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentProfileBindingServiceTests,AgentProfileVersionPolicyServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests,PublishedProfileRoutingModelValidatorTests test` 可通过，已覆盖 Agent 类型约束、子 Agent 绑定与运行时装配。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,AgentProfileVersionPolicyServiceTests,PublishedProfileRoutingModelValidatorTests test` 可通过，已覆盖 Agent 创建删除、`MAIN` 唯一性和主入口校验。

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
