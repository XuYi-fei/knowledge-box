# Progress

## 当前阶段

- 主线能力已成型，当前重点是继续收口文档治理、管理端运营能力，以及聊天体验细节。
- 进度文档已改为“根总览 + 模块子进度”结构；根文档只保留项目级导航、全局重点和共享注意点。

## 当前重点

- 文档治理闭环继续收口：审核策略、失败可观测性、重复治理和导入运维仍是近期主线。
- 用户体验继续打磨：聊天流式细节、公开文库阅读体验和用户工具扩展仍有持续迭代空间。
- 真实环境联调继续补齐：Redis、邮件、OSS、模型配置与部署链路仍需持续验证。

## 模块索引

- [聊天与用户对话](docs/progress/chat/progress.md)：聊天主链路、会话、SSE、引用与对话页交互状态。
- [文档治理](docs/progress/document-governance/progress.md)：导入、审核、标签分类、专栏、索引和重复治理状态。
- [公开文库](docs/progress/public-library/progress.md)：`/articles`、公开文档详情、专栏阅读与公开目录体验状态。
- [工具平台](docs/progress/tool-platform/progress.md)：用户工具目录、执行链路、schema 驱动配置与工具模板状态。
- [管理端与可观测性](docs/progress/admin-observability/progress.md)：模型目录、Agent Profile、Hooks、Trace、后台布局与运营视图状态。
- [部署与运行时](docs/progress/deploy-runtime/progress.md)：多环境构建、发布脚本、远程平铺部署和本地运行链路状态。

## 最近全局验证

- `npm --prefix frontend run build` 已多轮通过，覆盖聊天、公开文库、工具平台、文档审核与 Trace 管理页等近期主线改动。
- `mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `package` 已通过，覆盖聊天编排、文档治理、工具平台、Trace 与公开文档接口。
- 本地 `java -jar backend/backend-app/target/knowledge-box-backend-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=18081` 已验证可启动，`/api/public/system/availability` 返回 `UP`。
- 当前沙箱下全量 PostgreSQL 集成测试仍可能因本机数据库连接受限失败；这属于环境限制，不是最近文档拆分导致的行为回归。

## 全局注意点

- `application-local.yml` 仅在 `local` profile 生效，且视为用户本地敏感配置，不要覆盖。
- 模块任务先读根 `progress.md`，再读对应 `docs/progress/<module>/progress.md`；根文档只保留索引与共享状态。
- “关于”tab 的更新日志来自数据库；独立功能完成后要补增量 changelog 往 `about_release_note` 写数据。
- Git 提交默认使用中文“简短标题 + 详细正文”；正文尽量完整说明对应 bug/优化/功能、问题背景和解决办法。
- 每次提交后都要回到对应模块 progress 检查并补齐当前进度；若进度因此变更，也要继续提交这些文档更新。
- 发布包中的语雀 bootstrap seed 若通过 `sourceMarkdownPath` 指向 `tmp/yuque-batch/full-*` 正文，不能只打包 `bootstrap-seeds/`，必须保留整棵 `tmp/yuque-batch/`。
