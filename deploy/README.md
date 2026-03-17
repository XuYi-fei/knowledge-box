# Deploy Guide

## 目标

面向 4C4G 服务器，采用“本地构建、服务器只运行”的部署方式：

- 本地构建后端 `jar`
- 本地构建前端 `dist`
- 将 `tmp/yuque-batch` 一起打进发布包
- 服务器仅负责解压、配置、运行

目标域名：`https://www.xuyifei.site`

## 目录说明

- `build-release.sh`: 本地构建发布包
- `deploy-remote-flat.sh`: 本地构建后直接上传到指定服务器并重启，适合 jar/dist 平铺部署
- `bin/start-backend.sh`: 服务器启动后端
- `bin/stop-backend.sh`: 停止后端
- `bin/start-backend-flat.sh`: 平铺目录部署时启动后端
- `bin/stop-backend-flat.sh`: 平铺目录部署时停止后端
- `templates/application-prod.yml`: 生产 Spring 外置配置模板
- `templates/knowledge-box.env.example`: 环境变量模板
- `templates/knowledge-box.service.example`: `systemd` 服务模板
- `templates/www.xuyifei.site.conf.example`: nginx 配置模板

## 本地打包

在仓库根目录执行：

```bash
./deploy/build-release.sh --profile production --keep-dir
```

输出目录默认位于：

- `dist/releases/knowledge-box-release-<timestamp>.tar.gz`
- `dist/releases/knowledge-box-release-<timestamp>/`

默认会包含：

- `app/knowledge-box-backend.jar`
- `frontend/dist/`
- `tmp/yuque-batch/`
- `deploy/` 下的启动脚本和模板
- `app/uploads/`（如果本地存在）

如果你只想复用现有构建产物：

```bash
./deploy/build-release.sh --skip-build --keep-dir
```

## 服务器目录建议

建议部署到：

- `/opt/knowledge-box`

解压示例：

```bash
mkdir -p /opt/knowledge-box
cd /opt/knowledge-box

tar -xzf knowledge-box-release-20260317-120000.tar.gz --strip-components=1
```

## 启动前配置

1. 复制配置模板

```bash
cp deploy/application-prod.yml config/application-prod.yml
cp deploy/knowledge-box.env.example config/knowledge-box.env
```

2. 修改数据库、Redis、模型、SMTP、JWT 等配置

至少应改：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DASHSCOPE_API_KEY`
- `KB_ADMIN_PASSWORD`
- `KB_AUTH_JWT_SECRET`
- `KB_INTEGRATION_CRYPTO_MASTER_KEY`

## 语雀启动导入

本发布方案默认会把 `tmp/yuque-batch` 一起带到服务器。

默认启动参数中已开启：

- `KB_DOCUMENT_BOOTSTRAP_ENABLED=true`
- `KB_DOCUMENT_BOOTSTRAP_SEED_DIRECTORY=./tmp/yuque-batch/bootstrap-seeds`
- `KB_DOCUMENT_BOOTSTRAP_SEED_DIRECTORY_PATTERN=*.seed.json`

因此，只要你的 seed 文件位于：

- `tmp/yuque-batch/bootstrap-seeds/*.seed.json`

服务器启动时就会自动导入到审核流。

注意：

- 这是幂等导入，依赖 `importKey`
- 同一批 seed 重启不会重复导入
- 导入失败默认不会阻断整个服务启动，因为 `KB_DOCUMENT_BOOTSTRAP_FAIL_FAST=false`
- 当前 seed 文件会通过 `sourceMarkdownPath` 继续读取 `tmp/yuque-batch/full-*` 下的正文文件，所以发布包需要保留整个 `tmp/yuque-batch/`，不能只复制 `bootstrap-seeds/`

## 启动后端

手动后台启动：

```bash
cd /opt/knowledge-box
./deploy/start-backend.sh --daemon
```

停止：

```bash
./deploy/stop-backend.sh
```

日志：

- `logs/backend.out.log`
- `logs/backend.pid`

补充说明：

- `./deploy/start-backend.sh` 默认以前台模式运行，适合 `systemd` 托管
- 手动运维时建议显式加 `--daemon`
- 生产环境尽量统一通过 `deploy/start-backend.sh` 或 `systemd` 启动，避免因为工作目录不同导致 bootstrap 相对路径失效

## systemd 部署

复制模板：

```bash
sudo cp deploy/knowledge-box.service.example /etc/systemd/system/knowledge-box.service
sudo systemctl daemon-reload
sudo systemctl enable knowledge-box
sudo systemctl start knowledge-box
```

## nginx 部署

复制模板：

```bash
sudo cp deploy/www.xuyifei.site.conf.example /etc/nginx/conf.d/www.xuyifei.site.conf
sudo nginx -t
sudo systemctl reload nginx
```

前端生产包默认建议使用相对 `/api` 请求，由 nginx 反代到 `127.0.0.1:8080`。

## 推荐资源参数

4C4G 机器建议从以下 JVM 参数起步：

```bash
JAVA_OPTS="-Xms512m -Xmx1536m -XX:MaxMetaspaceSize=320m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

不要在服务器上执行 Maven 和前端构建，尽量全部本地完成。

## 直接上传到服务器

如果你不想在服务器上解压 release 包，而是希望在本地构建后直接上传：

- 服务器：`124.221.214.211`
- 前端目录：`/home/ubuntu/repos/knowledge-box/dist`
- 后端 jar：`/home/ubuntu/repos/knowledge-box/knowledge-box-backend.jar`
- bootstrap 数据：`/home/ubuntu/repos/knowledge-box/tmp/yuque-batch`

可以直接在本地执行：

```bash
./deploy/deploy-remote-flat.sh
```

如果只想部署其中一部分：

```bash
./deploy/deploy-remote-flat.sh --frontend-only
./deploy/deploy-remote-flat.sh --backend-only
```

该脚本默认会：

- 本地执行后端 `package`
- 本地执行前端 `build --profile production`
- 上传 `frontend/dist/` 到服务器 `dist/`
- 上传后端 jar 到服务器根目录
- 上传整棵 `tmp/yuque-batch/`，保证 bootstrap seed 能继续读取 `full-*` 下正文
- 上传 `start-backend-flat.sh / stop-backend-flat.sh`
- 若本地存在 `config/application-prod.yml` 与 `config/knowledge-box.env`，优先直接覆盖上传到服务器 `config/`
- 远程执行停止旧进程并后台启动新 jar

行为约定：

- `--frontend-only`：只构建/上传前端 `dist`，不会上传 jar、配置、tmp，也不会重启后端
- `--backend-only`：只构建/上传后端 jar、配置、启动脚本和 `tmp/yuque-batch`，并按默认行为重启后端
- 不带参数：执行全量部署

推荐在本地先准备：

```bash
mkdir -p config
```

当前仓库已支持你在本地维护以下两个文件，并在部署时直接覆盖服务器端：

- `config/application-prod.yml`
- `config/knowledge-box.env`

如果本地不存在这些真实配置，脚本才会回退上传 example。

如果远端首次没有 `config/knowledge-box.env`，脚本会自动用 `config/knowledge-box.env.example` 初始化一份，再提示你补齐密钥字段。

常用参数：

```bash
./deploy/deploy-remote-flat.sh --skip-build
./deploy/deploy-remote-flat.sh --skip-restart
./deploy/deploy-remote-flat.sh --frontend-only
./deploy/deploy-remote-flat.sh --backend-only
./deploy/deploy-remote-flat.sh --mirror-tmp
./deploy/deploy-remote-flat.sh --host 124.221.214.211 --user ubuntu --remote-base /home/ubuntu/repos/knowledge-box
```

如果只想先看会上传哪些文件：

```bash
./deploy/deploy-remote-flat.sh --dry-run
```

补充说明：

- `--dry-run` 只打印 `ssh/rsync` 计划，不会真正修改远端目录
- `tmp/yuque-batch` 默认不会使用 `--delete` 清理远端；如需让远端该目录与本地完全一致，再显式加 `--mirror-tmp`

## 前端生产打包地址

如果你希望前端生产包直接请求 `https://www.xuyifei.site/api/**`，本地可使用：

```bash
cp frontend/.env.production.local.example frontend/.env.production.local
npm --prefix frontend run build -- --profile production
```

仓库当前已提供一个本机忽略的 `frontend/.env.production.local` 样例写法：

```env
VITE_API_BASE_URL=https://www.xuyifei.site/api
```

前端已做兼容处理：即使接口代码本身已经写成 `/api/...`，也不会再拼出重复的 `/api/api/...`。
