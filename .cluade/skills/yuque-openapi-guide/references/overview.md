# Yuque OpenAPI 总览

## 来源

- 本 skill 基于仓库根目录的本地 HTML 文档：`yuque_openapi_20251121_green.html`
- 文档内嵌 OpenAPI 3.1 结构，接口分组共 6 类：`user`、`search`、`group`、`doc`、`repo`、`statistic`

## 基础约定

- 服务器地址：`https://www.yuque.com`
- API 前缀：`/api/v2`
- 鉴权：请求头 `X-Auth-Token: <token>`
- 文档中的接口默认都要求 Token

最小请求模板：

```bash
curl -sS 'https://www.yuque.com/api/v2/user' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

## 核心标识

- `login`
  - 用户或团队的路径标识
  - 常见于 `/users/{login}/repos`、`/groups/{login}/repos`
- `group_login`
  - 团队路径标识
  - 常见于 `/repos/{group_login}/{book_slug}`
- `book_id`
  - 知识库 ID
- `book_slug`
  - 知识库路径
- `id`
  - 语义依接口而变，可能是用户 ID、文档 ID、版本 ID，或“文档 ID / 文档路径”
- `doc slug`
  - 文档路径，可在多个文档详情接口中替代文档 ID

## 推荐路径选择

- 已知团队路径和知识库路径时，优先用可读性更好的 slug 形式：
  - `/api/v2/repos/{group_login}/{book_slug}`
  - `/api/v2/repos/{group_login}/{book_slug}/docs`
- 先通过列表接口拿到数值 ID 后，也可以用 ID 形式：
  - `/api/v2/repos/{book_id}`
  - `/api/v2/repos/{book_id}/docs`
- 只知道单篇文档 ID 或路径时，可直接用：
  - `GET /api/v2/repos/docs/{id}`

## 常见枚举

- `public`
  - `0`: 私密
  - `1`: 公开
  - `2`: 企业内公开
- 文档 `format`
  - `markdown`
  - `html`
  - `lake`
  - 部分返回里还会见到 `lakesheet`
- TOC 节点 `type`
  - `DOC`
  - `LINK`
  - `TITLE`

## 错误码

- `400`: 请求参数非法
- `401`: Token 或 Scope 鉴权失败
- `403`: 无权限
- `404`: 实体不存在
- `422`: 参数校验失败
- `429`: 频率限制
- `500`: 服务器内部错误

## 安全边界

- 默认只读：`GET`
- 仅当用户明确要求修改语雀内容时才进入 `POST/PUT/DELETE`
- 不要在回答或代码里写死真实 Token
- 读取个人知识库时，优先先取当前用户信息，再列 repo，再列 docs，再取 doc detail

## 主要注意事项

- `POST /repos/.../docs` 创建文档后，不会自动出现在知识库目录里；还要再调一次 TOC 更新接口。
- `PUT /repos/.../toc` 删除目录节点时，不会删除关联文档本身。
- `optional_properties=hits,tags,latest_version_id` 只在文档列表每页数量不超过 `100` 时有效。
- `GET /api/v2/search` 的 `page` / `offset` 在文档里都写成页码语义，说明存在歧义；实际调用时要谨慎验证。
