# Frontend Configuration Guide

## 目标

本文档说明 `frontend/` 的前端配置方式，覆盖本地开发、多环境打包、Nginx 部署，以及常见排查手段。

当前前端已经支持通过 `profile` 动态选择配置：

```bash
npm run dev -- --profile development
npm run build -- --profile staging
npm run preview -- --profile production
```

`profile` 默认会作为 Vite 的 `mode` 使用，因此会自动加载对应的 `.env.<profile>` 文件。

## 配置文件结构

前端目录下建议维护以下文件：

- `.env.example`: 通用默认样例
- `.env.development.example`: 开发环境样例
- `.env.staging.example`: 预发环境样例
- `.env.production.example`: 生产环境样例
- `.env.development.local.example`: 开发环境本地覆盖样例
- `.env.staging.local.example`: 预发环境本地覆盖样例
- `.env.production.local.example`: 生产环境本地覆盖样例

实际使用时，建议按需复制为真实文件：

```bash
cp .env.development.example .env.development
cp .env.development.local.example .env.development.local
```

## Vite 环境变量加载优先级

以 `--profile production` 为例，Vite 的常见优先级可以理解为：

1. 系统环境变量
2. `.env.production.local`
3. `.env.production`
4. `.env.local`
5. `.env`

因此建议：

- 团队共享值放 `.env.<profile>`
- 个人机器临时覆盖放 `.env.<profile>.local`
- 不要把真实敏感地址或临时联调地址直接写进共享文件

## 当前使用的前端变量

### `VITE_API_BASE_URL`

用途：控制前端请求后端 API 的基地址。

当前代码逻辑位于：

- `src/lib/api.ts`

行为规则：

- 若配置为完整地址，例如 `http://localhost:8080`，请求会发往该绝对地址
- 若配置为空字符串，前端会直接请求相对路径 `/api/**`
- 若生产环境由 Nginx 同域反代 `/api`，推荐将其留空

示例：

开发环境：

```env
VITE_API_BASE_URL=http://localhost:8080
```

生产环境同域反代：

```env
VITE_API_BASE_URL=
```

## 运行命令

### 开发

```bash
cd frontend
npm install
npm run dev -- --profile development
```

### 预发打包

```bash
cd frontend
npm run build -- --profile staging
```

### 生产打包

```bash
cd frontend
npm run build -- --profile production
```

### 本地预览生产包

```bash
cd frontend
npm run preview -- --profile production
```

## 推荐的 profile 使用策略

### development

适用场景：

- 本地开发
- 本地联调后端
- 本地调试浏览器问题

建议配置：

```env
VITE_API_BASE_URL=http://localhost:8080
```

### staging

适用场景：

- 预发构建
- 测试环境联调
- 功能验证

建议配置：

```env
VITE_API_BASE_URL=https://staging-api.example.com
```

### production

适用场景：

- 正式环境打包
- Nginx 部署

建议优先采用同域反代：

```env
VITE_API_BASE_URL=
```

## Nginx 部署建议

### 方案一：前后端同域部署，推荐

优点：

- 不需要额外处理 CORS
- 前端配置最简单
- 只需要维护一个对外域名

前端配置：

```env
VITE_API_BASE_URL=
```

Nginx 负责：

- `/` 提供前端静态资源
- `/api/` 转发给 Spring Boot
- `/uploads/` 转发静态上传资源

示例配置文件见：

- `nginx/knowledge-box.frontend.conf.example`

### 方案二：前后端分域部署

适合：

- 前端部署到 CDN
- API 部署到独立域名

前端配置：

```env
VITE_API_BASE_URL=https://api.example.com
```

此时需要保证后端 `knowledge-box.web.allowed-origins` 已包含前端域名。

## 常见操作示例

### 1. 本地开发连本地后端

```bash
cp .env.development.example .env.development
npm run dev -- --profile development
```

### 2. 本地开发临时连测试后端

```bash
cp .env.development.local.example .env.development.local
npm run dev -- --profile development
```

### 3. 本地构建生产包，交给运维部署

```bash
cp .env.production.example .env.production
npm run build -- --profile production
```

### 4. 生产环境使用 Nginx 反代 `/api`

```bash
cp .env.production.example .env.production
npm run build -- --profile production
```

并确保 `.env.production` 中：

```env
VITE_API_BASE_URL=
```

## 排查建议

### 页面打开后所有接口都请求到 `localhost`

通常是：

- 你当前打包时使用的 profile 仍然指向本地地址
- 你复制的是 `.env.example`，但没有创建 `.env.production`
- 你误用了旧的 `dist` 目录，没有重新 build

### 请求地址正确，但浏览器报跨域

通常是：

- 采用了前后端分域方案
- 后端 `knowledge-box.web.allowed-origins` 未加入前端域名

### 同域部署却还在请求绝对地址

请检查：

- `.env.production` 中的 `VITE_API_BASE_URL` 是否真的为空
- 构建命令是否是 `npm run build -- --profile production`
- Nginx 是否已更新到最新前端产物

### 修改 `.env` 后不生效

请检查：

- `vite dev` 是否已经重启
- 当前命令使用的是不是对应 profile
- 是否有 `.env.<profile>.local` 覆盖了共享配置

## 建议约束

- 共享仓库只提交 `*.example` 样例文件
- 真实环境文件由部署流程或运维侧注入
- 生产环境优先使用同域反代 `/api`
- 本地临时覆盖优先使用 `.env.<profile>.local`
- 不建议依赖 `npm run build --profile=production` 这种 npm config 风格，推荐固定使用 `npm run build -- --profile production`
