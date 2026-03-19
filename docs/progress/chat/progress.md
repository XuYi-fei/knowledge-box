# Chat Progress

## 当前阶段

- 聊天主链路已稳定可用，当前重点是继续打磨流式颗粒度、未完成会话恢复和 Markdown/代码块展示细节。

## 已完成

- 用户侧已具备登录后的知识库问答、会话持久化、历史恢复、删除会话与 SSE 流式输出。
- 聊天主链路已切到 AgentScope Java ReActAgent，支持前置知识检索、tool calling、reasoning/tool/citation 展示与 trace。
- 聊天链路现已支持通过 AgentScope `SubAgentTool` 调用绑定的原子子 Agent；子 Agent 自身仍可装配 Tool / MCP / Skill，并在统一 trace 中留下独立执行过程。
- 聊天链路现已支持 Agent 版本级运行时环境变量；Tool 执行、MCP 占位符解析和子 Agent 装配都可按当前 Agent 版本注入 `INLINE / PROCESS_ENV` 配置。
- 新增 `web-search` Tool、`tavily-search` Skill 与 `web-search-agent` bootstrap 配置；当 Agent 需要公网信息时可优先走 Tavily，缺少密钥时自动回退直接网页搜索。
- 现已支持通过 `knowledge-box.agent.runtime-env-check.*` 在启动期扫描 Agent envVars 与绑定依赖的必填 requirement，提前发现缺 key 配置。
- `PROCESS_ENV` 现在会优先读宿主环境变量，缺失时再回退读取 Spring `Environment`；因此本地开发可直接在 `application-local.yml` 中配置 `KB_TAVILY_API_KEY` 这类值。
- 对话主界面现已支持“停止回答”；用户主动中止后会立即截断当前流式输出，并把已有部分回答持久化为 `CANCELLED`，不会在刷新或会话恢复时被误续跑。
- 聊天回答中的 Markdown 代码块右上角现已支持一键复制，可直接把示例代码写入剪贴板。
- 聊天回答中的代码块复制入口已升级为双按钮工具条，支持“复制纯代码”和“复制 Markdown fenced code block”，复制成功后会给出更明确的按钮态和提示反馈。
- 回答下方引用已内联展示，并可跳转到公开文档详情查看正文，不再依赖右侧单独资料栏。
- 对话区已补齐稳定高度链与内部滚动约束；消息增多时只在会话主区和历史列表内滚动，不再把整页持续撑高。
- 回答下方“关联资料”摘要已压缩为更紧凑的两行预览，减少单条消息的纵向占用。
- 聊天主消息区已补齐细滚动条与右侧内边距，避免粗滚动条与靠右的用户消息头像发生视觉重叠；引用详情页“返回对话”按钮已移到左上区域，返回路径更符合阅读流。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天页、引用展示、会话区固定高度与内部滚动。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天页细滚动条、引用详情页左上返回按钮等本次 UI 修复。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖停止回答按钮、`CANCELLED` 消息态和停止后不自动恢复的前端状态机。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天消息代码块复制按钮，以及与公开文档/后台 Markdown 预览共用的代码块渲染组件。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖代码块双按钮复制工具条、更明确的复制成功反馈，以及 fenced code block 复制能力。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `package` 可通过，已覆盖聊天编排与引用链路。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ChatOrchestratorTests,AssistantTurnAwaitServiceTests test` 可通过，已覆盖 stop 接口、取消态快照和 legacy 等待分支的 `CANCELLED` 终态回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentProfileBindingServiceTests,AgentProfileVersionPolicyServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests,PublishedProfileRoutingModelValidatorTests test` 可通过，已覆盖主链路对子 Agent 装配与约束校验的回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentCapabilityAssemblyServiceTests,AgentProfileBindingServiceTests,ConfigBundleAdminServiceTests,AgentConfigAdminServiceTests,ChatOrchestratorTests test` 可通过，已覆盖运行时环境变量注入、web-search Tool 装配与配置 Bundle v2 相关回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentRuntimeEnvStartupCheckRunnerTests,AgentProfileBindingServiceTests,ConfigBundleAdminServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests test` 可通过，已覆盖启动期 env 自检、缺少宿主环境变量与 requirement 缺失告警。

## 待继续推进

- 继续打磨流式输出颗粒度与异常/未知流事件的兜底展示。
- 补齐未完成会话恢复与断链后的用户感知。
- 继续优化 Markdown、代码块与长回答的阅读体验。

## 关键注意点

- AgentScope 事件分支需显式覆盖 `REASONING/TOOL_RESULT/HINT/SUMMARY/AGENT_RESULT/ALL`，不要依赖默认分支兜底。
- `@ToolParam` 必须显式声明 `name`。
- AgentScope 工具执行可能切线程，关键上下文不要只依赖 `ThreadLocal`。
- 子 Agent 运行时按 `SubAgentTool` 作为工具注册；当前仅支持单层调用，且父 Agent 只能绑定 `ATOMIC` 版本。
- Agent 运行时环境变量当前按 `profileVersionId` 独立解析，子 Agent 不继承父 Agent 的密钥；若子 Agent 也依赖外部服务，需要单独绑定自己的 `envVars`。
- 停止回答后的助手消息使用 `CANCELLED` 独立终态；刷新恢复逻辑只能续跑 `PENDING/STREAMING`，不要把 `CANCELLED` 再次接回 SSE。
