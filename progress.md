# Progress

## 当前阶段

- 主线能力已成型，当前重点是继续收口文档治理、管理端运营能力，以及聊天体验细节。

## 已完成

- 用户侧已具备登录、知识库问答、会话持久化、SSE 流式输出、历史恢复、会话删除与“关于”tab。
- 聊天主链路已切到 AgentScope Java ReActAgent，支持前置知识检索、tool calling、reasoning/tool/citation 展示与 trace。
- 用户侧引用已内联到回答下方，并支持跳转公开文档详情；文档详情页已具备 Markdown 渲染、标题锚点、大纲、高亮定位与返回对话链路。
- 用户侧已升级为路由级顶部工作区 header：标题与副标题固定在左侧，右侧 tabs 基于 URL 在 `主页 / 关于` 间切换；“关于”已从聊天页左下角迁出，改为独立 `about` 页面渲染。
- 用户侧 header 已新增 `工具` tab 与独立 `/tools` 页面：首批内置 `Base64 编码`、`Base64 解码`、`MD5 摘要`，支持前端本地执行与后端统一执行两类模式。
- 主页对话区已补齐稳定高度链：`chat-shell -> chat-content -> chat-main -> chat-card -> chat-messages` 统一使用 `flex + min-height: 0` 约束，消息增多时仅主会话区内部滚动，不再把整页不断撑高。
- 主页工作区外层已显式禁用窗口级滚动：`app-shell / app-shell-main / user-workspace-shell / user-workspace-content / chat-shell / chat-content` 统一补上 `overflow: hidden`，避免浏览器窗口继续接管滚动条，保证滚动只发生在主消息区和左侧历史列表内部。
- 聊天回答下方“关联资料”的预览文本已进一步收缩：引用摘要改为两行截断并缩小字号，减少单条回答引用区占用的垂直空间。
- 后端已建立 Spring Boot + Liquibase + PostgreSQL/pgvector 基础工程，并拆分为 `backend-app/service/repository/domain` 四个 Maven 子模块。
- 管理端已接入模型目录、Agent Profile Version、Hooks、Trace、文档治理，以及动态 Tool/MCP/Skill 绑定管理。
- 管理端已新增用户工具目录与工具执行日志页面；后端新增 `app_tool_definition / app_tool_execution_log`、用户工具执行接口、Redis 限流与后端执行型工具审计日志；前端已补齐工具目录/日志加载失败态、筛选回第一页、基础表单约束联动，以及可视化 schema 配置器与结果展示配置。
- 用户工具页的输入表单与结果展示已切到 schema 驱动：`SERVER` 工具新增/改配可通过后台元数据直接生效，无需为了字段布局和结果展示单独重部署前端；仅新增全新的 `CLIENT` 执行器时仍需前端发版。
- 已补充一批通用模板工具并通过初始化脚本自动注册：`URL 编码/解码`、`SHA-256 摘要`、`JSON 格式化/压缩`、`时间戳转换`；对应前端 `CLIENT` handler 已实现，可直接在工具页使用。
- 前端启动/打包已支持按 profile 动态选择配置：`npm run dev/build/preview -- --profile <name>` 或 `npm run ... --profile=<name>`，并补充 `development/staging/production` 环境变量模板，便于 nginx 部署与多环境构建。
- 文档治理链路已落地：文档上传、审核流、分类标签、Markdown 预览/编辑、图片转存、向量写入、索引重建与 bootstrap 初始化导入。
- 管理端文档审核已支持批量审核通过；Trace 已支持列表、详情、删除、时间线、瀑布图与通俗解读视图。
- 初始化数据已补充前台可登录管理员账号 `admin@example.com / admin123`。
- 已提供语雀导入辅助脚本与说明：`scripts/yuque_kb_migrate.py`、`scripts/README.md`。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天页、文档详情页、文档审核页、Trace 管理页、顶部 header/独立 About、主页固定高度与内部滚动、聊天引用样式，以及本次新增的用户工具页、header `工具` tab、管理端工具目录与工具执行日志页面。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖用户工具页 schema 驱动输入/结果渲染与管理端可视化 schema 配置器。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖新增的 URL、SHA-256、JSON、时间戳 `CLIENT` 模板工具执行逻辑。
- 前端：`npm --prefix frontend run build -- --profile production` 可通过，已覆盖 profile 选择脚本与构建时动态配置加载。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `package` 可通过，已覆盖用户工具数据模型、Liquibase、公开/管理接口、执行器注册链路，以及近期聊天编排、Trace、文档审核相关改动。
- 后端：`mvn -q -pl backend/backend-app -am -Dtest=Md5DigestAppToolExecutorTests -Dsurefire.failIfNoSpecifiedTests=false test` 可通过。
- 后端：全量 `mvn -q -pl backend/backend-app -am test` 在当前沙箱环境下因无法连本机 PostgreSQL（`SocketException: Operation not permitted`）失败，非本次代码编译错误。
- 本地启动：`java -jar backend/backend-app/target/knowledge-box-backend-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=18081` 已验证可启动；bootstrap 导入按 `importKey` 幂等跳过已有数据。
- 本地可用性：`/api/public/system/availability` 可正常返回 `UP`。
- 辅助脚本：语雀 OpenAPI 脚本与导入脚本 `--help` 已验证可执行。

## 待继续推进

- 文档审核策略继续细化：权限颗粒度、审核原因规范化、失败可观测性、运营闭环。
- 聊天体验继续打磨：流式颗粒度、未完成会话恢复、Markdown/代码块渲染细节、异常流事件兜底展示。
- Redis、邮件发送、OSS 与真实模型配置的本地联调继续补齐。

## 关键注意点

- `application-local.yml` 仅在 `local` profile 生效，且视为用户本地敏感配置，不要覆盖。
- “关于”tab 的更新日志来自数据库；独立功能完成后要补增量 changelog 往 `about_release_note` 写数据。
- AgentScope 高风险点：`@ToolParam` 需显式写 `name`；事件分支要显式覆盖 `REASONING/TOOL_RESULT/HINT/SUMMARY/AGENT_RESULT/ALL`；`enableThinking(false)` 时不要再设置 `thinkingBudget`；动态注册 Tool/MCP 前先建 tool group；Hook 调试字段可能为 `null`，不要直接用 `Map.of(...)` 组装。
- 文档治理高风险点：审核/生成异步线程要在 `afterCommit` 后启动；标签绑定写入前要去重，删旧绑定优先 bulk delete 或显式 flush；DashScope embedding 单批上限按 `10` 控制；bootstrap 导入必须依赖稳定 `importKey` 做幂等。
- Git 提交信息默认使用中文。
