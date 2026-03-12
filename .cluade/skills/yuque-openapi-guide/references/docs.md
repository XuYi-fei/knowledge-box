# 文档与历史版本接口

## 文档列表

### `GET /api/v2/repos/{book_id}/docs`
### `GET /api/v2/repos/{group_login}/{book_slug}/docs`

参数：

- `offset`：默认 `0`
- `limit`：默认 `100`，最大 `100`
- `optional_properties`

`optional_properties` 支持：

- `hits`
- `tags`
- `latest_version_id`

注意事项：

- 官方文档说明：当每页数量超过 `100` 时，`optional_properties` 会失效。

## 创建文档

### `POST /api/v2/repos/{book_id}/docs`
### `POST /api/v2/repos/{group_login}/{book_slug}/docs`

请求体：

- `slug`
- `title`
- `public`
- `format`
- `body`：必填

注意事项：

- 创建后不会自动进入知识库目录。
- 如果需要让文档出现在目录里，还要再调用 TOC 更新接口。

示例请求体：

```json
{
  "slug": "quick-start",
  "title": "快速开始",
  "format": "markdown",
  "body": "# 快速开始\\n\\n这里是正文"
}
```

## 获取文档详情

### `GET /api/v2/repos/docs/{id}`
### `GET /api/v2/repos/{book_id}/docs/{id}`
### `GET /api/v2/repos/{group_login}/{book_slug}/docs/{id}`

参数：

- 路径 `id`：文档 ID 或文档路径
- 查询 `page_size` / `page`：主要用于数据表场景

### 读取正文时重点字段

- `format`
- `body`
- `body_html`
- `body_lake`
- `body_sheet`
- `body_table`

建议读取策略：

- 普通 Markdown 文档：优先看 `body`
- 需要 HTML 渲染：看 `body_html`
- `Sheet`：看 `body_sheet`
- `Table`：看 `body_table`

### `V2DocDetail` 关键字段

- `id` / `slug` / `title`
- `description`
- `format`
- `public`
- `status`
- `created_at` / `updated_at` / `content_updated_at`
- `published_at` / `first_published_at`
- `latest_version_id`

### 官方文档给出的 `body_sheet` 结构示例

```json
{
  "version": "1.0",
  "data": [
    {
      "name": "Sheet1",
      "index": 0,
      "rowCount": 100,
      "colCount": 4,
      "table": [
        ["参数名", "类型", "必填", "默认值"],
        ["name", "string", "1", ""],
        ["flag", "boolean", "0", "false"]
      ]
    }
  ]
}
```

## 更新文档

### `PUT /api/v2/repos/{book_id}/docs/{id}`
### `PUT /api/v2/repos/{group_login}/{book_slug}/docs/{id}`

可改字段：

- `slug`
- `title`
- `public`
- `format`
- `body`

注意事项：

- 如果只是读取和同步内容，不需要调用更新接口。
- `id` 既可以是数值 ID，也可以是文档路径。

## 删除文档

### `DELETE /api/v2/repos/{book_id}/docs/{id}`
### `DELETE /api/v2/repos/{group_login}/{book_slug}/docs/{id}`

注意事项：

- destructive 操作
- 默认仅在用户明确要求删除时才考虑

## 历史版本

### `GET /api/v2/doc_versions`

参数：

- `doc_id`：必填

说明：

- 返回最近 `100` 个已发布版本
- 默认按时间倒序

### `GET /api/v2/doc_versions/{id}`

返回重点：

- `format`
- `body`
- `body_html`
- `body_asl`
- `diff`

适用场景：

- 对比版本差异
- 拉取最近发布版本快照
- 根据 `latest_version_id` 继续追版本详情
