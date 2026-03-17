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
- 已补充 `frontend/CONFIGURATION.md`、`.env.<profile>.local.example` 与 nginx 示例配置，前端多环境构建、同域反代部署和本地覆盖策略已有独立文档说明。
- 已补充 `deploy/` 发布目录：支持本地构建后端 `jar` + 前端 `dist` 的 release bundle，内置 `www.xuyifei.site` 生产模板、4C4G JVM 参数、后端启动/停止脚本，并将 `tmp/yuque-batch` 与 bootstrap seeds 一起打包到服务器启动导入链路。
- 已补充远程平铺部署脚本 `deploy/deploy-remote-flat.sh`：支持本地构建后直接把前端 `dist`、后端 `jar`、`tmp/yuque-batch` 同步到 `124.221.214.211:/home/ubuntu/repos/knowledge-box`，并用 `start-backend-flat.sh` 远程重启。
- 前端 API 基址拼接已补齐 `/api` 容错：`VITE_API_BASE_URL` 可配置为域名或域名 `/api`，生产环境直连 `https://www.xuyifei.site/api` 时不会再出现重复 `/api/api`。
- 远程部署脚本在服务器缺少 `config/knowledge-box.env` 时，会先基于 example 初始化，再提示补齐数据库密码和其他密钥字段。
- 已支持本地维护 `config/application-prod.yml` 与 `config/knowledge-box.env` 并在远程部署时直接覆盖服务器端配置；当前本地已生成一份可用的 `knowledge-box.env`，仅数据库密码留空待补。
- 已修复 `deploy/bin/start-backend-flat.sh` 对 `config/knowledge-box.env` 只 `source` 不导出的问题；当前会自动导出 `.env` 里的 `DB_*`、`KB_*` 等变量，远程平铺部署启动时无需把配置文件改写成 `export KEY=...` 格式。
- 已修复 `deploy/bin/stop-backend-flat.sh` 只发 `TERM` 不等待退出的问题；当前平铺部署重启会阻塞等待旧后端实例退出，超时后可按配置回退到强杀，避免旧进程未停完就拉起新实例。
- 已补充 `scripts/cleanup_stuck_bootstrap_reviews.py`，用于清理因导入中断而卡在 `PROCESSING/CHUNKING` 的 bootstrap 审核单，释放 `importKey` 幂等占位，便于重启后重新导入。
- 文档治理链路已落地：文档上传、审核流、分类标签、Markdown 预览/编辑、图片转存、向量写入、索引重建与 bootstrap 初始化导入。
- 管理端文档审核已支持批量审核通过；Trace 已支持列表、详情、删除、时间线、瀑布图与通俗解读视图。
- 初始化数据已补充前台可登录管理员账号 `admin@example.com / admin123`。
- 已提供语雀导入辅助脚本与说明：`scripts/yuque_kb_migrate.py`、`scripts/README.md`。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖聊天页、文档详情页、文档审核页、Trace 管理页、顶部 header/独立 About、主页固定高度与内部滚动、聊天引用样式，以及本次新增的用户工具页、header `工具` tab、管理端工具目录与工具执行日志页面。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖用户工具页 schema 驱动输入/结果渲染与管理端可视化 schema 配置器。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖新增的 URL、SHA-256、JSON、时间戳 `CLIENT` 模板工具执行逻辑。
- 前端：`npm --prefix frontend run build -- --profile production` 可通过，已覆盖 profile 选择脚本与构建时动态配置加载。
- 前端：`npm --prefix frontend run build -- --profile production` 可通过，已覆盖新增前端配置文档与本地覆盖模板后的构建链路。
- 发布脚本：`bash -n deploy/build-release.sh && bash -n deploy/bin/start-backend.sh && bash -n deploy/bin/stop-backend.sh` 可通过。
- 远程平铺部署脚本：`bash -n deploy/deploy-remote-flat.sh deploy/bin/start-backend-flat.sh deploy/bin/stop-backend-flat.sh` 与 `./deploy/deploy-remote-flat.sh --help` 可通过。
- 前端：`frontend/.env.production.local` 配置 `VITE_API_BASE_URL=https://www.xuyifei.site/api` 后，`npm --prefix frontend run build -- --profile production` 可通过。
- 远程平铺部署脚本：在本地存在 `config/application-prod.yml` / `config/knowledge-box.env` 时，`./deploy/deploy-remote-flat.sh --skip-build --dry-run` 已确认优先上传真实配置文件而非 example。
- 远程平铺启动脚本：`bash -n deploy/bin/start-backend-flat.sh` 可通过；基于临时假 `java` 进程的本地验证已确认 `config/knowledge-box.env` 中的 `DB_URL/DB_USERNAME/DB_PASSWORD/SPRING_PROFILES_ACTIVE` 会导出到子进程。
- 远程平铺停止脚本：`bash -n deploy/bin/stop-backend-flat.sh` 可通过；基于临时假后端进程的本地验证已确认脚本会在发出 `TERM` 后等待旧进程真正退出再返回。
- 数据清理：`python3 scripts/cleanup_stuck_bootstrap_reviews.py` dry-run 与 `--apply` 已验证可用；已实际删除 `81` 条卡在 `PROCESSING/CHUNKING` 的 `yuque:*` bootstrap 审核单，当前库内仅剩 `9` 条 `APPROVED` 审核记录。
- 发布脚本：`./deploy/build-release.sh --skip-build --keep-dir --output-dir /tmp/knowledge-box-release-test` 可生成 release 目录与 tar.gz，并确认包含后端 `jar`、前端 `dist`、生产模板、启动脚本，以及供 bootstrap 导入使用的整棵 `tmp/yuque-batch/`。
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
- 发布包中的语雀 bootstrap seed 若通过 `sourceMarkdownPath` 指向 `tmp/yuque-batch/full-*` 正文，不能只打包 `bootstrap-seeds/`，必须保留整棵 `tmp/yuque-batch/`。
- Git 提交信息默认使用中文。
