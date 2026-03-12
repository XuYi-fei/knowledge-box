# 知识库接口

## 列知识库

### `GET /api/v2/groups/{login}/repos`
### `GET /api/v2/users/{login}/repos`

用途：

- 列出团队或个人名下的知识库

常用参数：

- `offset`：分页偏移，默认 `0`
- `limit`：每页数量，最大 `100`
- `type`：`Book` 或 `Design`
- `filterByAbility=create_doc`：只返回具备建文档权限的知识库

建议保留字段：

- `id`
- `slug`
- `name`
- `namespace`
- `public`
- `items_count`
- `content_updated_at`

## 创建知识库

### `POST /api/v2/groups/{login}/repos`
### `POST /api/v2/users/{login}/repos`

请求体：

- `name`：必填
- `slug`：必填
- `description`
- `public`
- `enhancedPrivacy`

注意事项：

- 团队知识库可以设置 `enhancedPrivacy`
- 默认不建议在“读取知识库”任务里走创建接口

示例请求体：

```json
{
  "name": "知识库名称",
  "slug": "kb-slug",
  "description": "简介",
  "public": 0
}
```

## 获取知识库详情

### `GET /api/v2/repos/{book_id}`
### `GET /api/v2/repos/{group_login}/{book_slug}`

常用返回字段：

- `id`
- `slug`
- `name`
- `description`
- `namespace`
- `toc_yml`
- `public`
- `items_count`

## 更新知识库

### `PUT /api/v2/repos/{book_id}`
### `PUT /api/v2/repos/{group_login}/{book_slug}`

可改字段：

- `name`
- `slug`
- `description`
- `public`
- `toc`

关于 `toc`：

- 可以直接批量用 Markdown 目录更新知识库目录
- 这是整库级更新能力，修改前要格外谨慎

文档给出的 `toc` Markdown 示例：

```markdown
- [新手指引]()
  - [语雀是什么](about)
  - [常见问题](faq)
- [基础功能]()
  - [工作台](dashboard)
  - [如何设置自定义路径](nkt888)
  - [外链](http://www.alipay.com)
```

## 删除知识库

### `DELETE /api/v2/repos/{book_id}`
### `DELETE /api/v2/repos/{group_login}/{book_slug}`

注意事项：

- 这是 destructive 操作
- 默认不要主动建议或执行，除非用户明确要求删除
