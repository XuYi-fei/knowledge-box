# Progress

## 当前阶段

- 主线能力已基本成型，当前重点是继续收口文档治理闭环、管理端运营能力，以及聊天体验细节。

## 核心进度

- 用户侧已具备登录、知识库问答、会话持久化、SSE 流式输出、历史恢复、删除会话与“关于”tab。
- 后端已建立 Spring Boot + Liquibase + PostgreSQL/pgvector 基础工程，并已拆分为 `backend-app/service/repository/domain` 四个 Maven 子模块。
- 大模型主链路已切换到 AgentScope Java ReActAgent，并接入 tool calling、查询路由、reasoning/tool/citation 展示与 trace。
- 聊天主链路已修正 AgentScope 流式事件订阅，最终回答正文恢复走 `SUMMARY -> delta/fullContent` 主输出区，thinking/tool 仅留在小字摘要区。
- 管理端已接入模型目录、Agent Profile Version、Hooks、Trace、文档治理与动态 Tool/MCP/Skill 绑定管理。
- 文档治理链路已落地：文档上传、审核流、分类标签、索引重建、Markdown 预览/编辑、图片转存、向量写入与 bootstrap 初始化导入。
- 初始化数据已补充前台可登录管理员账号 `admin@example.com`，可直接用 `admin123` 登录用户侧首页。
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
- 文档治理相关高频坑仍需注意：
  - 审核/生成异步线程要在 `afterCommit` 后启动
  - 标签绑定写入前要去重，删除旧绑定优先 bulk delete 或显式 flush
  - DashScope embedding 单批上限按 `10` 控制
  - bootstrap 导入必须依赖稳定 `importKey` 做幂等
- Git 提交信息默认使用中文。

## 最近验证

- 前端验证：`npm --prefix frontend run build` 通过（包含后端异常提示条、备案 footer、审核时间格式化改动，以及健康探测失败不自动重试修复）。
- 前端验证：`npm --prefix frontend run build` 通过（含登录页技术性 SMTP 提示移除与验证码失败文案收口）。
- 后端编译验证：`mvn -q -pl backend/backend-app -am -DskipTests compile` 通过。
- 后端打包验证：`mvn -q -pl backend/backend-app -am -DskipTests package` 通过。
- 后端单测抽样：`mvn -q -pl backend/backend-app -am -Dtest=AgentCapabilityAssemblyServiceTests -Dsurefire.failIfNoSpecifiedTests=false test` 与 `AgentProfileBindingServiceTests` 通过。
- 一键回归脚本验证：`bash scripts/quick-regression.sh` 通过（后端编译 + 关键单测 + 前端构建）。
- 后端集成验证：`mvn -q -pl backend/backend-app -am -Dtest=KnowledgeBoxPostgresIntegrationTests,KnowledgeBoxProductionLiquibaseIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含 `admin@example.com` 初始化账号与前台密码登录回归）。
- 后端全量测试：`mvn -q -pl backend/backend-app -am test` 通过（本机 PostgreSQL + pgvector 已就绪，含 `KnowledgeBoxPostgresIntegrationTests` 与 `KnowledgeBoxProductionLiquibaseIntegrationTests`）。
- 后端定向回归：`mvn -q -pl backend/backend-app -am -Dtest=ChatOrchestratorTests -Dsurefire.failIfNoSpecifiedTests=false test` 通过（含流式事件订阅集合与 AgentScope 事件消费回归）。
- 语雀 skill 脚本验证：`python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py --help` 与语法编译检查通过。
- 导入脚本命令校验：`python3 scripts/yuque_kb_migrate.py --help` 与三个子命令 `--help` 均可正常执行。

## 待继续推进

- 文档审核策略继续细化：批量审核、权限颗粒度、审核原因规范化、可观测性增强。
- 聊天体验继续打磨：流式颗粒度、未完成会话恢复、Markdown/代码块渲染细节。
- 聊天体验继续打磨：流式颗粒度、未完成会话恢复、Markdown/代码块渲染细节，以及前端对异常/未知流事件的兜底展示。
- Redis、邮件发送、OSS 与真实模型配置的本地联调继续补齐。
