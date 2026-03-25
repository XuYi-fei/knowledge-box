# Chat Progress

## 当前阶段

- 聊天主链路已稳定可用，当前重点是继续打磨流式颗粒度、未完成会话恢复和 Markdown/代码块展示细节。

## 已完成

- 用户侧已具备登录后的知识库问答、会话持久化、历史恢复、删除会话与 SSE 流式输出。
- 用户侧工作区已新增“知识入库”入口，支持独立上传/粘贴工作台、草稿轮询、Agent 建议确认，以及提交到文档审核链路。
- 知识入库大文件自动分流到 `/ingest/tasks/:taskId`，新增任务总览、阶段进度、子产物列表与详情预览 UI，并可实时取消任务或查看部分完成文档。
- 知识入库工作台现已补齐明确的“任务中心”入口与 `/ingest/tasks` 页面；用户无需再记忆参数化任务路径，可直接查看最近异步任务、进度与详情入口。
- 草稿确认提交审核后，成功态现已提供“前往审核页”按钮；若当前用户没有管理员权限，仍沿用现有后台登录/权限拦截链路。
- 草稿确认表单中的分类现已允许直接输入临时新分类，不再只能从既有分类中选择。
- 知识入库草稿与任务子文档的 Markdown 预览现已对超链接使用蓝色下划线样式，链接可读性更明确。
- 大 PDF 任务页现已把文本提取进度细化到逐页反馈；任务顶部会直接显示“正在读取第 X/Y 页”，并带出当前页文本片段预览，前端轮询也已缩短到 1 秒，读取阶段的体感更接近实时。
- 知识入库工作台与任务页现已恢复纵向滚动；长表单、长任务列表和 Markdown 预览在用户工作区内可正常上下浏览，不再被外层容器截断。
- 用户工作区与公开页共用的备案号脚注现已回到页面内容流底部，不再因为 `AppShell` 高度锁定而视觉上固定在窗口底部；知识入库、关于页、公开文章与其他共用外壳页面都会沿用该修复。
- 聊天主链路已切到 AgentScope Java ReActAgent，支持前置知识检索、tool calling、reasoning/tool/citation 展示与 trace。
- 聊天链路现已支持通过 AgentScope `SubAgentTool` 调用绑定的原子子 Agent；子 Agent 自身仍可装配 Tool / MCP / Skill，并在统一 trace 中留下独立执行过程。
- 聊天链路现已支持 Agent 版本级运行时环境变量；Tool 执行、MCP 占位符解析和子 Agent 装配都可按当前 Agent 版本注入 `INLINE / PROCESS_ENV` 配置。
- 新增 `web-search` Tool、`tavily-search` Skill 与 `web-search-agent` bootstrap 配置；当 Agent 需要公网信息时可优先走 Tavily，缺少密钥时自动回退直接网页搜索。
- 聊天主链路已把知识库能力切换为“按 Tool 绑定启用”：只有当前 Agent 版本实际绑定了 `KnowledgeBaseSearchTool`，才会在该轮把 `searchKnowledgeBase` 注册给 ReActAgent；MAIN 是否必须先检索则直接由当前版本 `systemPrompt` 约束，未绑定的 Entry Agent 会直接走通用回答链路。
- Agent 版本运行时当前只保留基础 `systemPrompt`；原先按场景拼接的四类知识库模板已移除，导入导出与 bootstrap 示例也同步收口。
- 现已支持通过 `knowledge-box.agent.runtime-env-check.*` 在启动期扫描 Agent envVars 与绑定依赖的必填 requirement，提前发现缺 key 配置。
- `PROCESS_ENV` 现在会优先读宿主环境变量，缺失时再回退读取 Spring `Environment`；因此本地开发可直接在 `application-local.yml` 中配置 `KB_TAVILY_API_KEY` 这类值。
- 对话主界面现已支持“停止回答”；用户主动中止后会立即截断当前流式输出，并把已有部分回答持久化为 `CANCELLED`，不会在刷新或会话恢复时被误续跑。
- 用户侧现已新增独立的 `Agent 调试` 页：登录用户可选择 `ENTRY + PUBLISHED + publicDebug=true` 的入口 Agent 发起调试会话，并按入口隔离历史记录与 trace 摘要。
- 当调试入口 Agent 下线、改为非公开或改成非 `ENTRY` 时，旧调试会话仍可查看，但前端与后端都会禁止继续创建新调试对话。
- 聊天回答中的 Markdown 代码块右上角现已支持一键复制，可直接把示例代码写入剪贴板。
- 聊天回答中的代码块复制入口已升级为双按钮工具条，支持“复制纯代码”和“复制 Markdown fenced code block”，复制成功后会给出更明确的按钮态和提示反馈。
- 助手消息现已新增“回复过程”时间线，用竖向步骤卡片展示思考、工具调用和最终回答，不再只显示单行思考摘要。
- 助手消息“回复过程”时间线中的思考步骤图标现已区分进行中与已完成；流式期间仅当前思考步骤保持旋转，思考完成后会切换为完成态图标。
- 助手消息“回复过程”展开态现已优先展示后端结构化 `processDetails`；思考步骤会显示更完整的阶段说明，工具调用会展示调用参数、调用 ID 和执行结果，不再与折叠摘要重复。
- 助手消息“回复过程”现已支持整块展开与收起；默认对进行中的回答自动展开，对已结束回答默认折叠，同时保留内部单步骤详情的二级展开。
- 聊天消息的过程持久化现已只保留真实模型 reasoning 与工具调用详情；编排层注入的“已接收/已装载/查询路由/上下文提示”等系统节点不再写入消息过程，也不会在历史恢复时反复出现。
- 子 Agent Tool 现已兼容 `query`/`message` 两种入参；像 `web-search-agent` 这类原子 Agent 即使被父 Agent 以 `query` 调用，也会自动映射到 AgentScope 原生要求的 `message`，避免报参数校验失败。
- 回答下方引用已内联展示，并可跳转到公开文档详情查看正文，不再依赖右侧单独资料栏。
- 对话区已补齐稳定高度链与内部滚动约束；消息增多时只在会话主区和历史列表内滚动，不再把整页持续撑高。
- 回答下方“关联资料”摘要已压缩为更紧凑的两行预览，减少单条消息的纵向占用。
- 聊天主消息区已补齐细滚动条与右侧内边距，避免粗滚动条与靠右的用户消息头像发生视觉重叠；引用详情页“返回对话”按钮已移到左上区域，返回路径更符合阅读流。
- `Agent 调试` 页左侧边栏的选择框、标签和底部按钮现已补齐宽度约束与文本省略，避免按钮样式溢出侧栏宽度。
- 聊天前端页面现已完成业务级拆分：`PublicChatPage` / `AgentDebugPage` 只保留入口差异，重复的会话状态机、SSE 消费、消息合并和通用布局已抽到共享聊天工作区模块，避免两份页面继续并行堆积逻辑。
- 聊天后端主编排现已完成职责拆分：`ChatOrchestrator` 只保留入口编排、订阅与任务调度，回答生成主流程已抽到 `ChatGenerationExecutor`，知识库执行计划、citation 聚合与回答收尾已抽到 `ChatKnowledgeBasePlanService`。
- MAIN 默认 `systemPrompt` 现已新增图片去重约束：若最终回答包含图片，同一图片 URL 在单条回答中只能输出一次；默认创建逻辑、bootstrap 示例与初始化/升级 changelog 已同步。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天页、引用展示、会话区固定高度与内部滚动。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖知识入库页的路由、导航和确认表单接线。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天页细滚动条、引用详情页左上返回按钮等本次 UI 修复。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖 `Agent 调试` 页左侧边栏按钮/标签/用户区的宽度约束修复。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖停止回答按钮、`CANCELLED` 消息态和停止后不自动恢复的前端状态机。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天消息代码块复制按钮，以及与公开文档/后台 Markdown 预览共用的代码块渲染组件。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖代码块双按钮复制工具条、更明确的复制成功反馈，以及 fenced code block 复制能力。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖新增的 `Agent 调试` tab、独立调试页、按入口隔离的会话恢复键与 trace 摘要侧栏。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖主页与 Agent 调试页新增的助手消息“回复过程”时间线组件。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖“回复过程”时间线中思考步骤图标由进行中切换为完成态的前端改动。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天页与 Agent 调试页对结构化 `processDetails` 的消费，以及展开态思考/工具详情展示。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖移除 optimistic 假思考节点后，聊天页与 `Agent 调试` 页只展示真实 reasoning/tool 过程。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖“回复过程”整体折叠开关、步骤计数展示，以及聊天页与 `Agent 调试` 页的共用时间线组件回归。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖知识入库上传自动分流、`/ingest/tasks/:taskId` 任务页、阶段/子产物列表与 Markdown 预览回归。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖知识入库任务中心 `/ingest/tasks`、确认后跳审核页按钮、分类临时新建与草稿链接高亮回归。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖任务页顶部显示实时 PDF 读取摘要、当前页片段预览、1 秒轮询，以及任务类型透传 `summaryText` 的编译回归。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖知识入库工作台与任务页恢复纵向滚动后的页面编译回归。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖 `AppShell` 备案号脚注回到页面底部后的共享布局编译回归。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天共享工作区拆分后 `PublicChatPage` / `AgentDebugPage` 的页面瘦身、会话恢复、SSE 消费与通用布局回归。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `package` 可通过，已覆盖聊天编排与引用链路。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 可通过，已覆盖知识库 Tool 绑定判定、`systemPrompt` 直传和相关运行时装配回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ChatOrchestratorTests,AssistantTurnAwaitServiceTests test` 可通过，已覆盖 stop 接口、取消态快照和 legacy 等待分支的 `CANCELLED` 终态回归。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 可通过，已覆盖 `process_details_json` 持久化、SSE/会话详情透传与时间线结构化详情格式化。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ChatOrchestratorTests,AssistantTurnAwaitServiceTests test` 可通过，已覆盖 `ALL/TOOL_RESULT/HINT/SUMMARY/AGENT_RESULT` 事件消费及工具详情展开回归。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 可通过，已覆盖去除编排层注入 reasoning 节点、移除 `HINT` 落库，以及仅保留真实 reasoning/tool 的消息过程持久化。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ChatOrchestratorTests,AssistantTurnAwaitServiceTests test` 可通过，已覆盖 `HINT` 不再写入消息过程、工具详情仍保留，以及聊天取消态回归。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 可通过，已覆盖子 Agent Tool 的 `query -> message` 兼容包装与注册链路。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=CompatibleSubAgentToolTests,AgentCapabilityAssemblyServiceTests test` 可通过，已覆盖子 Agent Tool 接受 `query` 别名、缺少参数时报清晰错误，以及工具装配回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentProfileBindingServiceTests,AgentProfileVersionPolicyServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests,PublishedProfileRoutingModelValidatorTests test` 可通过，已覆盖主链路对子 Agent 装配与约束校验的回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentCapabilityAssemblyServiceTests,AgentProfileBindingServiceTests,ConfigBundleAdminServiceTests,AgentConfigAdminServiceTests,ChatOrchestratorTests test` 可通过，已覆盖运行时环境变量注入、web-search Tool 装配与配置 Bundle v2 相关回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentRuntimeEnvStartupCheckRunnerTests,AgentProfileBindingServiceTests,ConfigBundleAdminServiceTests,AgentCapabilityAssemblyServiceTests,ChatOrchestratorTests test` 可通过，已覆盖启动期 env 自检、缺少宿主环境变量与 requirement 缺失告警。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,AgentProfileVersionPolicyServiceTests,ChatOrchestratorTests test` 可通过，已覆盖调试入口约束、按入口停止回答与删除 Agent 时的聊天/trace 清理回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,AgentConfigAdminServiceTests,ConfigBundleAdminServiceTests,ChatOrchestratorTests test` 可通过，已覆盖知识库 Tool 绑定装配、无知识库绑定入口跳过路由/检索，以及去除知识库模板字段后的导入导出回归。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 可通过，已覆盖移除知识库分类路由、删除 `KnowledgeBaseRoutingService` 后的聊天编排主链路编译回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ChatOrchestratorTests test` 可通过，已覆盖“绑定 KB tool 即启用知识库工具、未绑定则关闭、且不再走分类模型/兜底检索”的主链路回归。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ChatKnowledgeBasePlanServiceTests,ChatOrchestratorTests,AssistantTurnAwaitServiceTests,CompatibleSubAgentToolTests,AgentCapabilityAssemblyServiceTests test` 可通过，已覆盖聊天主链路重构后的执行计划拆分、回答生成执行器兼容层与共享单测回归。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminCommandServiceTests,ChatOrchestratorTests test` 可通过，已覆盖 MAIN 默认 prompt 新增“同一图片 URL 只输出一次”的约束，以及聊天入口侧兼容回归。

## 待继续推进

- 继续打磨流式输出颗粒度与异常/未知流事件的兜底展示。
- 补齐未完成会话恢复与断链后的用户感知。
- 继续打磨知识入库工作台的用户反馈细节，例如更细的分析进度、失败提示、草稿恢复体验和任务中心筛选能力。
- 继续优化 Markdown、代码块与长回答的阅读体验。

## 关键注意点

- AgentScope 事件分支需显式覆盖 `REASONING/TOOL_RESULT/HINT/SUMMARY/AGENT_RESULT/ALL`，不要依赖默认分支兜底。
- `@ToolParam` 必须显式声明 `name`。
- AgentScope 工具执行可能切线程，关键上下文不要只依赖 `ThreadLocal`。
- 用户侧知识入库入口当前是独立工作台，不嵌入聊天输入区；确认后的产物进入待审核，而不是直接发布。
- 子 Agent 运行时按 `SubAgentTool` 作为工具注册；当前仅支持单层调用，且父 Agent 只能绑定 `ATOMIC` 版本。
- 当前聊天主链路不再使用知识库分类模型；若希望 MAIN Agent 查知识库，需直接给当前版本绑定 `KnowledgeBaseSearchTool`，并在 MAIN 的 `systemPrompt` 中明确要求先检索再回答。
- Agent 运行时环境变量当前按 `profileVersionId` 独立解析，子 Agent 不继承父 Agent 的密钥；若子 Agent 也依赖外部服务，需要单独绑定自己的 `envVars`。
- 停止回答后的助手消息使用 `CANCELLED` 独立终态；刷新恢复逻辑只能续跑 `PENDING/STREAMING`，不要把 `CANCELLED` 再次接回 SSE。
- 用户侧多入口调试不要复用主聊天入口的 session 存储键；最近会话恢复必须按 `userId + profileCode` 维度隔离，否则不同 Entry Agent 会串会话。
