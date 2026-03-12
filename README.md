# Knowledge Box

Knowledge Box 是一套个人知识库系统。

核心形态是一个网页问答入口：用户直接输入问题，系统基于知识文档、检索结果和 Agent 编排生成回答，并附带引用来源。与此同时，管理员可以在后台维护 Agent 配置版本、知识文档、Tools / MCP / Skills、Hooks 和运行追踪信息。

当前仓库已经包含一套可启动的前后端实现，以及数据库初始化脚本、本地对象存储适配器和基础页面。公开聊天链路已接入基于 AgentScope Java 的 ReAct Agent，向量检索持久化写入与外部 Tool / MCP 执行器仍有扩展空间。

## 系统概览

### 面向普通用户的能力

- 通过邮箱验证码 + 密码登录后进入聊天页面
- 以问答形式返回结果
- 每次回答附带引用来源和命中文档片段

### 面向管理员的能力

- 轻量登录进入管理后台
- 查看 Agent 配置版本和发布状态
- 上传 Markdown 文档
- 上传 Markdown 所引用的本地图片资源，后端会转存并改写图片链接
- 查看文档处理任务、Hooks、Tools / MCP / Skills、运行 Trace

### 当前技术栈

- 前端：`React 19`、`Vite`、`TypeScript`、`Ant Design 5`、`@ant-design/pro-components`
- 后端：`Java 21`、`Spring Boot 3.5.6`、`Spring Security`、`Spring Data JPA`、`Liquibase`
- AI / RAG 基础设施：`AgentScope Java 1.0.9`、`Spring AI Alibaba 1.1.2.0`（Embedding / DashScope Starter）、`pgvector`
- 数据库：`PostgreSQL 16+`
- 本地文件存储：后端内置 `local storage adapter`

## 仓库结构

- `backend/`: Spring Boot 后端服务，负责公开问答接口、管理接口、配置读取、Liquibase 和本地文件存储
- `frontend/`: React 管理后台和公开问答页面

## 当前已实现内容

- 公开问答页和管理后台骨架
- 管理端 Basic Auth 登录
- 用户邮箱验证码登录
- Agent Profile / Version、文档、Hooks、Trace 等管理页面
- `POST /api/public/chat` 与 `POST /api/public/chat/stream` 接口
- 基于 AgentScope ReActAgent + 知识库检索工具的真实对话调用链
- Markdown 上传接口
- Markdown 中本地图片引用的转存与链接改写
- Liquibase 初始化脚本和演示数据

## 当前未完成内容

- 多 Provider 模型路由与更细粒度实时流式输出优化
- 文档切片后的真实向量写入与混合检索查询
- Tool / MCP / Hook 的真实执行器
- 管理端对数据库真实增删改查的完整闭环

## 启动前需要准备什么

### 1. 安装基础软件

本地需要先安装以下工具：

- `JDK 21`
- `Maven 3.9+`
- `Node.js 20+`
- `npm 10+`
- `PostgreSQL 16+`

可以用下面的命令检查版本：

```bash
java -version
mvn -version
node -v
npm -v
psql --version
```

### 2. 准备 PostgreSQL 和 pgvector

Knowledge Box 当前默认使用 PostgreSQL，并依赖 `pgvector` 扩展。

你需要：

1. 选择一个准备给项目使用的数据库
2. 在数据库中启用 `vector` 扩展

示例：

```sql
CREATE DATABASE knowledge_box;
\c knowledge_box;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 准备 DashScope API Key

当前后端会通过 AgentScope Java 的 DashScope ChatModel 调用大模型，应用启动和聊天都需要有效的 DashScope Key。

你需要准备：

- `DASHSCOPE_API_KEY`

如果你暂时只想看项目骨架、不接真实模型，也可以先把它写成占位值，但未来接通真实模型时必须替换为有效 Key。

## 本地配置

### 后端配置

当前配置分成两层：

- [application.yml](/Users/xuyifei/repos/knowledge-box/backend/src/main/resources/application.yml): 放跨环境共享且应显式存在的基础配置，例如 Redis key 命名空间规划
- [application-local.yml](/Users/xuyifei/repos/knowledge-box/backend/src/main/resources/application-local.yml): 放本地开发环境实际使用的数据库、Redis、SMTP、管理员账号等配置；IDEA 以 `local` profile 启动时会读取

共享基础配置当前包含：

```yaml
knowledge-box:
  redis:
    keys:
      auth:
        verification-code: ${KB_REDIS_KEY_AUTH_VERIFICATION_CODE:knowledge-box:auth:verification-code}
        verification-cooldown: ${KB_REDIS_KEY_AUTH_VERIFICATION_COOLDOWN:knowledge-box:auth:verification-cooldown}
      chat:
        session-state: ${KB_REDIS_KEY_CHAT_SESSION_STATE:knowledge-box:chat:session-state}
        stream-state: ${KB_REDIS_KEY_CHAT_STREAM_STATE:knowledge-box:chat:stream-state}
      rate-limit:
        auth-send-code: ${KB_REDIS_KEY_RATE_LIMIT_AUTH_SEND_CODE:knowledge-box:rate-limit:auth-send-code}
        public-chat-submit: ${KB_REDIS_KEY_RATE_LIMIT_PUBLIC_CHAT_SUBMIT:knowledge-box:rate-limit:public-chat-submit}
```

当前本机建议配置如下：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: ${KB_SERVLET_MULTIPART_MAX_FILE_SIZE:20MB}
      max-request-size: ${KB_SERVLET_MULTIPART_MAX_REQUEST_SIZE:100MB}
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/knowledge_box}
    username: ${DB_USERNAME:postgres}
    password:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      password: ${REDIS_PASSWORD:}
      timeout: 3s
  mail:
    host: smtp.qq.com
    port: 587
    username: ${MAIL_USERNAME:your_qq_mail@qq.com}
    password: ${MAIL_PASSWORD:your_qq_smtp_auth_code}
    protocol: smtp
    default-encoding: UTF-8
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: false
          ssl:
            enable: false
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:}

knowledge-box:
  admin:
    username: ${KB_ADMIN_USERNAME:admin}
    password: ${KB_ADMIN_PASSWORD:change-this-admin-password}
  auth:
    jwt-secret: ${KB_AUTH_JWT_SECRET:replace-with-a-long-random-jwt-secret}
  mail:
    from-address: ${KB_MAIL_FROM_ADDRESS:${MAIL_USERNAME:your_qq_mail@qq.com}}
    from-personal: ${KB_MAIL_FROM_PERSONAL:Knowledge Box}
  web:
    allowed-origins:
      - http://localhost:5173
  storage:
    provider: local
    local-base-path: backend/uploads
    public-base-url: http://localhost:8080/uploads
    oss:
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      bucket: your-bucket
      access-key-id: ${KB_STORAGE_OSS_ACCESS_KEY_ID:}
      access-key-secret: ${KB_STORAGE_OSS_ACCESS_KEY_SECRET:}
      public-base-url: https://your-bucket.oss-cn-hangzhou.aliyuncs.com
      path-prefix: knowledge-box
  chat:
    top-k: 6
    stream-delay: 150ms
    knowledge-base-routing:
      enabled: true
      force-enable-regexes: []
      force-disable-regexes:
        - "(?i).*(你是什么模型|你是(什么|哪个)|你用的什么模型|what model are you|which model are you|who are you|what are you).*"
        - "(?i).*(写个|写一个|实现|给我.*代码|write (a|an)|implement).*(快速排序|quicksort|quick\\s*sort).*"
        - "(?i).*\\b(quicksort|quick\\s*sort|merge\\s*sort|heap\\s*sort|bubble\\s*sort|binary\\s*search|bfs|dfs)\\b.*"
    dashscope-compatible:
      base-url: ${KB_DASHSCOPE_COMPATIBLE_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
      force-model-regexes:
        - "(?i)^qwen3\\.5-.*$"
```

关键配置项说明：

- `spring.datasource.*`: PostgreSQL 连接信息
- `spring.servlet.multipart.max-file-size`: 单文件上传大小上限（默认 `20MB`）
- `spring.servlet.multipart.max-request-size`: 单次请求上传总大小上限（默认 `100MB`）
- `spring.data.redis.*`: 邮箱验证码和发送频控使用的 Redis 连接信息，现已在 `application-local.yml` 中显式声明
- `knowledge-box.redis.keys.*`: Redis key 前缀分组配置，按认证、聊天、限流等类型拆分命名空间
- `spring.mail.*`: QQ 邮箱 SMTP 配置，`password` 需要填写 QQ 邮箱的 SMTP 授权码，不是网页登录密码
- `spring.ai.dashscope.api-key`: DashScope API Key
- `knowledge-box.admin.username`: 管理员用户名
- `knowledge-box.admin.password`: 管理员初始化密码（仅在数据库中该管理员密码为空时写入，不会覆盖已改过的密码）
- `knowledge-box.auth.jwt-secret`: 用户登录 JWT 密钥，必须显式配置
- `knowledge-box.mail.from-address`: 验证码邮件发件邮箱地址，默认建议与 `spring.mail.username` 一致
- `knowledge-box.mail.from-personal`: 验证码邮件发件人昵称，可选
- `knowledge-box.web.allowed-origins`: 前端访问后端的 CORS 白名单
- `knowledge-box.storage.provider`: 当前默认 `local`
- `knowledge-box.storage.local-base-path`: 本地上传文件目录
- `knowledge-box.storage.public-base-url`: 文件对外访问前缀
- `knowledge-box.storage.oss.endpoint`: OSS Endpoint（如 `https://oss-cn-hangzhou.aliyuncs.com`）
- `knowledge-box.storage.oss.bucket`: OSS Bucket 名称
- `knowledge-box.storage.oss.access-key-id`: OSS AccessKey ID
- `knowledge-box.storage.oss.access-key-secret`: OSS AccessKey Secret
- `knowledge-box.storage.oss.public-base-url`: OSS 资源访问域名（建议 CDN 或 Bucket 公网地址）
- `knowledge-box.storage.oss.path-prefix`: OSS 对象前缀目录
- `knowledge-box.chat.top-k`: 默认检索 TopK
- `knowledge-box.chat.stream-delay`: SSE 分块输出节奏
- `knowledge-box.chat.knowledge-base-routing.enabled`: 是否开启“通用问题跳过知识库”路由
- `knowledge-box.chat.knowledge-base-routing.force-enable-regexes`: 命中后强制启用知识库工具
- `knowledge-box.chat.knowledge-base-routing.force-disable-regexes`: 命中后强制禁用知识库工具和 fallback 检索（适合通用编程问答）
- `knowledge-box.chat.dashscope-compatible.base-url`: DashScope OpenAI 兼容端点（用于需兼容端点的模型）
- `knowledge-box.chat.dashscope-compatible.force-model-regexes`: 命中后改走兼容端点的模型名规则（默认包含 `qwen3.5-*`）
- `knowledge-box.retrieval.embedding-batch-size`: 向量写入分批大小（默认 `10`；当前 DashScope 链路会强制上限 `10`，用于避免 `batch size is invalid`）
- `knowledge-box.document.bootstrap.enabled`: 是否在应用启动时按 seed 文件初始化文档审核单（默认关闭）
- `knowledge-box.document.bootstrap.seed-file`: 初始化 seed 文件路径（支持 `file:` 或 `classpath:`）
- `knowledge-box.document.bootstrap.fail-fast`: 启动导入失败时是否终止应用启动
- `knowledge-box.document.bootstrap.operator-username`: 导入审核单使用的管理员用户名（会自动解析/创建 admin_operator）

### 前端配置

复制前端环境变量样例：

```bash
cp frontend/.env.example frontend/.env
```

默认内容：

```bash
VITE_API_BASE_URL=http://localhost:8080
```

如果后端不是跑在 `8080` 端口，需要同步改这里。

## 如何启动系统

### 第一步：启动数据库

确保 PostgreSQL 已经启动，并且：

- `vector` 扩展已启用

对于当前这台机器，最省事的做法是直接使用 `postgres` 这个现成数据库。

### 第二步：启动后端

方式一：使用 IDE

- 以 `local` Profile 启动 [KnowledgeBoxApplication.java](/Users/xuyifei/repos/knowledge-box/backend/src/main/java/com/knowledgebox/KnowledgeBoxApplication.java)

方式二：命令行启动

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

后端默认地址：

- `http://localhost:8080`

健康检查：

- `http://localhost:8080/actuator/health`

### 第三步：启动前端

第一次启动先安装依赖：

```bash
cd frontend
npm install
```

开发模式启动：

```bash
npm run dev
```

前端默认地址：

- `http://localhost:5173`

### 第四步：访问页面

用户登录页：

- `http://localhost:5173/`

管理后台登录页：

- `http://localhost:5173/admin/login`

管理端登录信息取决于你实际配置的：

- `knowledge-box.admin.username`
- `knowledge-box.admin.password`

管理员登录后，前端会把 Basic Auth 信息保存到浏览器本地，并在访问 `/api/admin/**` 时自动带上。

管理端支持登录后在线改密：

- UI：右上角 `改密` 按钮
- API：`POST /api/admin/me/password`，请求体为 `{ "currentPassword": "...", "newPassword": "..." }`
- 改密成功后立即生效，后续重启不会被初始化脚本覆盖

## 启动后的验证方式

### 验证后端是否正常

可以直接访问健康检查：

```bash
curl http://localhost:8080/actuator/health
```

也可以测试公开问答接口：

```bash
curl -X POST http://localhost:8080/api/public/chat \
  -H 'Content-Type: application/json' \
  -d '{"query":"这个系统支持什么能力？"}'
```

### 验证前端是否正常

- 进入公开问答页后，输入一个问题，应能看到流式回答、思考摘要和引用来源
- 进入管理后台后，应能看到 Dashboard、Agent 配置、知识文档、Hooks、Trace 等菜单

## 文档上传说明

当前上传方式为：

- 上传一个 Markdown 文件
- 如果 Markdown 中引用了本地图片，再额外上传对应图片文件

后端会执行以下动作：

1. 读取 Markdown 内容
2. 找出 Markdown 中的图片引用
3. 根据文件名匹配上传的图片资源
4. 将图片保存到本地上传目录
5. 把 Markdown 中的原始图片路径改写为可访问的 URL
6. 输出规范化后的 Markdown 文件

当前这部分主要是“规范化上传链路”，还没有把后续切片、embedding、向量入库完全接通。

## 语雀单文档迁移

推荐按“导出 -> 离线 OSS 转存 -> 生成 seed -> 启动自动导入审核流”执行。
相关脚本：

- [yuque_kb_migrate.py](/Users/xuyifei/repos/knowledge-box/scripts/yuque_kb_migrate.py)：第一步导出语雀文档
- [oss_rehost_markdown_images.py](/Users/xuyifei/repos/knowledge-box/scripts/oss_rehost_markdown_images.py)：第二步离线转存图片到 OSS（不依赖后端）
- [prepare_bootstrap_seed.py](/Users/xuyifei/repos/knowledge-box/scripts/prepare_bootstrap_seed.py)：第三步生成/更新启动导入 seed

### 1. 从语雀导出 Markdown（审查用）

```bash
python3 scripts/yuque_kb_migrate.py export-md \
  --token "$YUQUE_TOKEN" \
  --book-id 51241102 \
  --doc-id 238740054 \
  --output-md tmp/yuque-exports/spring-interview.raw.md \
  --output-meta tmp/yuque-exports/spring-interview.meta.json
```

### 2. 离线转存图片到 OSS（不依赖后端服务）

```bash
python3 scripts/oss_rehost_markdown_images.py \
  --input-md tmp/yuque-exports/spring-interview.raw.md \
  --output-md tmp/yuque-exports/spring-interview.rehosted.md \
  --manifest-json tmp/yuque-exports/spring-interview.oss-images.json \
  --endpoint "$KB_STORAGE_OSS_ENDPOINT" \
  --bucket "$KB_STORAGE_OSS_BUCKET" \
  --access-key-id "$KB_STORAGE_OSS_ACCESS_KEY_ID" \
  --access-key-secret "$KB_STORAGE_OSS_ACCESS_KEY_SECRET" \
  --public-base-url "$KB_STORAGE_OSS_PUBLIC_BASE_URL" \
  --path-prefix "${KB_STORAGE_OSS_PATH_PREFIX:-knowledge-box}"
```

说明：

- 图片链接会在本地 md 中直接改写为 OSS URL。
- 默认使用 `md5.ext` 作为对象名，支持对象级复用。

### 3. 生成启动导入 seed（不写 Liquibase）

```bash
python3 scripts/prepare_bootstrap_seed.py \
  --seed-json backend/bootstrap/document-seed.json \
  --input-md tmp/yuque-exports/spring-interview.rehosted.md \
  --title "Spring面试题" \
  --source-filename "spring-interview.md" \
  --visibility-type PUBLIC \
  --yuque-meta-json tmp/yuque-exports/spring-interview.meta.json
```

### 4. 启动后端并自动写入审核流（仅创建审核单）

```bash
cd backend
KB_DOCUMENT_BOOTSTRAP_ENABLED=true \
KB_DOCUMENT_BOOTSTRAP_SEED_FILE=bootstrap/document-seed.json \
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

说明：

- 启动导入只会创建审核单并走到 `PENDING_REVIEW`，不会自动 `approve`。
- seed 采用 `importKey` 幂等去重，重复启动不会重复创建同一条初始化数据。

## 语雀批量迁移（跨服务器）

如果你在另一台服务器执行导入，核心是把参数通过环境变量或命令行传入脚本，不依赖本地硬编码路径。

### 0. 先准备运行环境

```bash
export YUQUE_TOKEN='<your-yuque-token>'
export KB_BASE_URL='http://<your-kb-host>:8080'
export KB_ADMIN_USERNAME='admin'
export KB_ADMIN_PASSWORD='<admin-password>'
```

说明：

- `YUQUE_TOKEN`：语雀 personal token（`export-md` 会读取）。
- `KB_BASE_URL`：Knowledge Box 管理端 API 地址（`rehost-images` / `init-review` 会读取）。
- `KB_ADMIN_USERNAME` / `KB_ADMIN_PASSWORD`：管理端 Basic Auth 账号密码。

### 1. 批量处理时参数怎么传

针对每篇文档执行三步（可循环）：

```bash
# 1) 导出
python3 scripts/yuque_kb_migrate.py export-md \
  --book-id <BOOK_ID> \
  --doc-id <DOC_ID> \
  --output-md tmp/yuque-batch/<BOOK_ID>/<DOC_ID>/raw.md \
  --output-meta tmp/yuque-batch/<BOOK_ID>/<DOC_ID>/meta.json

# 2) 图片转存（调用 KB paste-image）
python3 scripts/yuque_kb_migrate.py rehost-images \
  --input-md tmp/yuque-batch/<BOOK_ID>/<DOC_ID>/raw.md \
  --output-md tmp/yuque-batch/<BOOK_ID>/<DOC_ID>/rehosted.md \
  --manifest-json tmp/yuque-batch/<BOOK_ID>/<DOC_ID>/images.json \
  --kb-base-url "$KB_BASE_URL" \
  --kb-admin-username "$KB_ADMIN_USERNAME" \
  --kb-admin-password "$KB_ADMIN_PASSWORD"

# 3) 创建审核单
python3 scripts/yuque_kb_migrate.py init-review \
  --input-md tmp/yuque-batch/<BOOK_ID>/<DOC_ID>/rehosted.md \
  --title "<DOC_TITLE>" \
  --source-filename "yuque-<BOOK_ID>-<DOC_ID>.md" \
  --yuque-meta-json tmp/yuque-batch/<BOOK_ID>/<DOC_ID>/meta.json \
  --kb-base-url "$KB_BASE_URL" \
  --kb-admin-username "$KB_ADMIN_USERNAME" \
  --kb-admin-password "$KB_ADMIN_PASSWORD" \
  --wait-status PENDING_REVIEW \
  --wait-timeout-seconds 240
```

建议：在 `init-review` 时通过 `--extension-json-file` 传入带 `importKey` 的 JSON（例如 `yuque:<bookId>:<docId>`），便于后续幂等去重。

### 2. 参数与默认值在哪里定义

- 脚本参数定义：`scripts/yuque_kb_migrate.py`
- 默认语雀地址：`DEFAULT_YUQUE_BASE_URL=https://www.yuque.com`
- 默认 KB 地址：`DEFAULT_KB_BASE_URL=http://localhost:8080`
- 默认导出目录：`tmp/yuque-exports`（可用 `--output-md/--output-meta` 覆盖）
- 脚本帮助：  
  `python3 scripts/yuque_kb_migrate.py --help`  
  `python3 scripts/yuque_kb_migrate.py export-md --help`  
  `python3 scripts/yuque_kb_migrate.py rehost-images --help`  
  `python3 scripts/yuque_kb_migrate.py init-review --help`

### 3. 启动期 seed 导入相关配置（可选）

如果你不走 `init-review`，而是走“后端启动自动导入 seed”，配置项如下：

- `knowledge-box.document.bootstrap.enabled`
- `knowledge-box.document.bootstrap.seed-file`
- `knowledge-box.document.bootstrap.fail-fast`
- `knowledge-box.document.bootstrap.operator-username`

对应位置：

- 共享默认配置：[application.yml](/Users/xuyifei/repos/knowledge-box/backend/src/main/resources/application.yml)
- 本地示例配置：[application-local.yml.example](/Users/xuyifei/repos/knowledge-box/backend/src/main/resources/application-local.yml.example)

## 已验证命令

本仓库当前已验证通过：

```bash
cd backend
mvn test
```

```bash
cd frontend
npm install
npm run build
```

## 常见问题

### 1. 后端启动时报数据库连接错误

通常是以下原因：

- PostgreSQL 没启动
- `knowledge_box` 数据库不存在
- 用户名或密码不对
- 没启用 `vector` 扩展

### 2. 发送邮箱验证码时报 Redis 或邮件服务错误

请检查：

- `spring.data.redis.*` 是否已经显式配置且 Redis 已启动
- `knowledge-box.redis.keys.*` 是否符合你的 Redis key 规划
- `spring.mail.*` 是否已填写 QQ 邮箱 SMTP 配置
- `MAIL_PASSWORD` 是否填写的是 QQ 邮箱 SMTP 授权码，而不是邮箱网页登录密码
- `knowledge-box.mail.from-address` 是否与实际发件邮箱一致，且为合法邮箱地址
- `knowledge-box.mail.from-personal` 若填写昵称，不要把昵称误填到 `from-address`

### 3. 后台登录后接口仍然 401

请检查：

- 前端登录时输入的用户名和密码是否与 `application-local.yml` 中一致
- 后端是否已经使用 `local` Profile 启动

### 4. 前端页面空白或接口请求失败

请检查：

- 前端 `.env` 中的 `VITE_API_BASE_URL` 是否正确
- 后端是否运行在 `http://localhost:8080`
- 浏览器控制台是否有跨域或网络错误

## 下一步建议

如果你准备继续完善这套系统，建议按下面顺序推进：

1. 把文档上传结果真正持久化到数据库
2. 接通 Markdown 切片、embedding 和 pgvector 写入
3. 扩展 AgentScope 多 Provider 模型路由与更细粒度流式输出
4. 打通 Tool、MCP、Hook 的实际执行
5. 补齐管理后台对 Tool/MCP/Skills 的发布闭环
