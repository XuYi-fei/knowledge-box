# 用户与搜索接口

## 用户相关

### `GET /api/v2/hello`

用途：

- 心跳
- 检查 Token 是否有效

### `GET /api/v2/user`

用途：

- 获取当前 Token 对应用户详情
- 后续读取个人知识库前，先从这里拿 `login`

关键返回字段：

- `id`
- `login`
- `name`
- `avatar_url`
- `books_count`
- `public_books_count`

### `GET /api/v2/users/{id}/groups`

用途：

- 查某个用户所在团队

参数：

- 路径 `id`：用户 `login` 或 ID
- 查询 `role`：`0` 管理员，`1` 成员
- 查询 `offset`：分页偏移

## 搜索相关

### `GET /api/v2/search`

必填参数：

- `q`：关键词
- `type`：`doc` 或 `repo`

可选参数：

- `scope`
- `page`
- `offset`
- `creatorId`
- `creator`

### `scope` 用法

- 当前用户或团队：不填
- 限定团队：`group_a`
- 限定知识库：`group_a/book_x`

### 返回结构

每项核心字段：

- `id`
- `type`
- `title`
- `summary`
- `url`
- `info`
- `target`

注意事项：

- `title` 和 `summary` 里的关键词可能带 `<em></em>` 高亮标签。
- `page` 和 `offset` 在官方文档里的语义描述存在重叠，接线上调用时建议先做一次小范围验证。
