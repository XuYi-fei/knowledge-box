# Deploy Runtime Progress

## 当前阶段

- 部署与运行时基础设施已可支撑本地和远程平铺部署，当前重点是继续补齐真实环境配置与稳定性验证。

## 已完成

- 前端已支持按 profile 动态选择配置，补齐了 `development/staging/production` 环境变量模板与独立配置文档。
- 已补充 `deploy/` 发布目录、release bundle 构建、远程平铺部署脚本，以及前后端分端发布能力。
- 已支持本地维护 `config/application-prod.yml` 与 `config/knowledge-box.env` 并在部署时覆盖服务器端配置。
- 已修复远程平铺启动时 `.env` 变量只 `source` 不导出、中文环境变量乱码，以及停止脚本不等待旧进程退出的问题。
- 已将 `tmp/yuque-batch` 与 bootstrap seeds 一并纳入发布包，保证服务器启动时的 bootstrap 导入链路可用。
- 本地启动链路已验证可跑通，`/api/public/system/availability` 可正常返回 `UP`。

## 已验证范围

- 前端：`npm --prefix frontend run build -- --profile production` 可通过，已覆盖 profile 选择脚本与动态配置加载。
- 发布脚本：`bash -n deploy/build-release.sh && bash -n deploy/bin/start-backend.sh && bash -n deploy/bin/stop-backend.sh` 可通过。
- 远程平铺部署脚本：`bash -n deploy/deploy-remote-flat.sh deploy/bin/start-backend-flat.sh deploy/bin/stop-backend-flat.sh` 与 `./deploy/deploy-remote-flat.sh --help` 可通过。
- 干跑：`./deploy/deploy-remote-flat.sh --skip-build --dry-run`、`--frontend-only`、`--backend-only` 已验证动作拆分正确。
- 本地启动：`java -jar backend/backend-app/target/knowledge-box-backend-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=18081` 已验证可启动，`/api/public/system/availability` 返回 `UP`。

## 待继续推进

- 补齐 Redis、邮件发送、OSS 与真实模型配置参与下的完整本地联调。
- 继续验证远程部署后的稳定性、回滚路径和运行期观测信息。
- 收口生产配置模板与实际服务器配置之间的差异，减少手工补配置步骤。

## 关键注意点

- `application-local.yml` 仅在 `local` profile 生效，且视为用户本地敏感配置，不要覆盖。
- 部署用本地真配置如 `config/knowledge-box.env`、`config/application-prod.yml` 必须保持在 `.gitignore` 中。
- 平铺部署若依赖 `.env` 中的中文值，启动脚本必须确保 locale 不是 `LANG=C` / `LC_ALL=C`。
- 平铺部署停止脚本不能只发 `TERM` 就返回，必须等待旧后端实例真正退出。
- 发布包中的语雀 bootstrap seed 若通过 `sourceMarkdownPath` 指向 `tmp/yuque-batch/full-*` 正文，必须保留整棵 `tmp/yuque-batch/`。
