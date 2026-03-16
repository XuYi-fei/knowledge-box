# Progress

## 当前阶段

- 主线能力已成型，当前重点是继续收口文档治理、管理端运营能力，以及聊天体验细节。

## 已完成

- 用户侧已具备登录、知识库问答、会话持久化、SSE 流式输出、历史恢复、会话删除与“关于”tab。
- 聊天主链路已切到 AgentScope Java ReActAgent，支持前置知识检索、tool calling、reasoning/tool/citation 展示与 trace。
- 用户侧引用已内联到回答下方，并支持跳转公开文档详情；文档详情页已具备 Markdown 渲染、标题锚点、大纲、高亮定位与返回对话链路。
- 用户侧已升级为路由级顶部工作区 header：标题与副标题固定在左侧，右侧 tabs 基于 URL 在 `主页 / 关于` 间切换；“关于”已从聊天页左下角迁出，改为独立 `about` 页面渲染。
- 后端已建立 Spring Boot + Liquibase + PostgreSQL/pgvector 基础工程，并拆分为 `backend-app/service/repository/domain` 四个 Maven 子模块。
- 管理端已接入模型目录、Agent Profile Version、Hooks、Trace、文档治理，以及动态 Tool/MCP/Skill 绑定管理。
- 文档治理链路已落地：文档上传、审核流、分类标签、Markdown 预览/编辑、图片转存、向量写入、索引重建与 bootstrap 初始化导入。
- 管理端文档审核已支持批量审核通过；Trace 已支持列表、详情、删除、时间线、瀑布图与通俗解读视图。
- 初始化数据已补充前台可登录管理员账号 `admin@example.com / admin123`。
- 已提供语雀导入辅助脚本与说明：`scripts/yuque_kb_migrate.py`、`scripts/README.md`。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，覆盖聊天页、文档详情页、文档审核页、Trace 管理页等近期改动。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖用户侧顶部 header、独立 AboutPage 路由与聊天页去内嵌 about tab 改动。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile`、`package`、`test` 均已通过；关键定向回归覆盖聊天编排、Trace、文档审核、Liquibase 与 PostgreSQL 集成链路。
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
