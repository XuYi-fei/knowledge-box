# 团队成员与统计接口

## 团队成员

### `GET /api/v2/groups/{login}/users`

用途：

- 拉团队成员列表

参数：

- `role`：`0` 管理员，`1` 成员，`2` 只读成员
- `offset`

### `PUT /api/v2/groups/{login}/users/{id}`

用途：

- 变更成员角色

请求体：

- `role`

### `DELETE /api/v2/groups/{login}/users/{id}`

用途：

- 删除团队成员

注意事项：

- 这两个接口都属于权限敏感写操作，除非用户明确要求，不要默认使用

## 团队统计

### `GET /api/v2/groups/{login}/statistics`

用途：

- 团队总体统计

### `GET /api/v2/groups/{login}/statistics/members`

常用参数：

- `name`
- `range`：`0` / `30` / `365`
- `page`
- `limit`：最大 `20`
- `sortField`
- `sortOrder`

### `GET /api/v2/groups/{login}/statistics/books`

常用参数：

- `name`
- `range`
- `page`
- `limit`
- `sortField`
- `sortOrder`

### `GET /api/v2/groups/{login}/statistics/docs`

常用参数：

- `bookId`
- `name`
- `range`
- `page`
- `limit`
- `sortField`
- `sortOrder`

适用场景：

- 团队内容运营分析
- 按成员、知识库、文档维度查看活跃度和产出

注意事项：

- 这些接口主要偏统计分析，不是“读取正文”的主链路。
- 如果用户目标只是读个人文档或同步知识库内容，通常不需要先读这组接口。
