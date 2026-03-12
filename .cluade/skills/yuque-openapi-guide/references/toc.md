# 知识库目录接口

## 获取目录

### `GET /api/v2/repos/{book_id}/toc`
### `GET /api/v2/repos/{group_login}/{book_slug}/toc`

返回项类型是 `V2TocItem`，关键字段：

- `uuid`
- `type`
- `title`
- `url`
- `doc_id`
- `level`
- `open_window`
- `visible`
- `prev_uuid`
- `sibling_uuid`
- `child_uuid`
- `parent_uuid`

用途：

- 遍历知识库结构
- 建立树形关系
- 获取后续更新目录所需的 `uuid`

## 更新目录

### `PUT /api/v2/repos/{book_id}/toc`
### `PUT /api/v2/repos/{group_login}/{book_slug}/toc`

### 公共字段

- `action`：必填
- `action_mode`
- `target_uuid`
- `visible`

### `action` 枚举

- `appendNode`
- `prependNode`
- `editNode`
- `removeNode`

### 创建场景

创建文档节点：

- `type=DOC`
- `doc_ids`

创建分组节点：

- `type=TITLE`
- `title`

创建外链节点：

- `type=LINK`
- `title`
- `url`
- `open_window`

### 移动场景

必填：

- `target_uuid`
- `node_uuid`

### 编辑场景

必填：

- `node_uuid`

可选：

- `type`
- `title`
- `url`
- `open_window`

### 删除场景

必填：

- `node_uuid`

注意事项：

- 官方文档明确说明：创建场景下不支持同级头插 `prependNode`
- 删除目录节点不会删除关联文档
- 删除时 `action_mode=sibling` 删除当前节点，`action_mode=child` 删除当前节点及子节点

示例：把新建文档追加到根节点

```json
{
  "action": "appendNode",
  "action_mode": "child",
  "type": "DOC",
  "doc_ids": [123456]
}
```

示例：新增分组节点

```json
{
  "action": "appendNode",
  "action_mode": "child",
  "type": "TITLE",
  "title": "新目录分组"
}
```
