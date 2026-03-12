# 读取个人语雀内容的推荐链路

## 目标一：确认 Token 是否可用

```bash
curl -sS 'https://www.yuque.com/api/v2/hello' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

用途：

- 验证 Token 可用性
- 在正式同步前先做轻量探活

## 目标二：拿到当前用户 login

```bash
curl -sS 'https://www.yuque.com/api/v2/user' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

重点关注返回字段：

- `data.id`
- `data.login`
- `data.name`

后续如果要读取“个人知识库”，通常把 `data.login` 带入 `/api/v2/users/{login}/repos`。

## 目标三：列出个人知识库

```bash
curl -sS 'https://www.yuque.com/api/v2/users/<login>/repos?limit=100&offset=0' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

建议先拿到每个 repo 的：

- `id`
- `slug`
- `name`
- `namespace`

## 目标四：列出某个知识库的文档

已知 `book_id`：

```bash
curl -sS 'https://www.yuque.com/api/v2/repos/<book_id>/docs?limit=100&optional_properties=hits,tags,latest_version_id' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

已知 `group_login/book_slug`：

```bash
curl -sS 'https://www.yuque.com/api/v2/repos/<group_login>/<book_slug>/docs?limit=100&optional_properties=hits,tags,latest_version_id' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

用途：

- 建立知识库内文档清单
- 同步时保留 `id`、`slug`、`title`、`content_updated_at`

## 目标五：读取单篇文档详情

只知道文档 ID 或路径：

```bash
curl -sS 'https://www.yuque.com/api/v2/repos/docs/<doc-id-or-slug>' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

已知 repo + doc：

```bash
curl -sS 'https://www.yuque.com/api/v2/repos/<book_id>/docs/<doc-id-or-slug>' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

重点字段：

- `data.format`
- `data.body`
- `data.body_html`
- `data.body_lake`
- `data.body_sheet`
- `data.body_table`

读取建议：

- 普通文档优先取 `body`
- 需要渲染 HTML 时取 `body_html`
- `Sheet` / `Table` 类型分别关注 `body_sheet` / `body_table`

## 目标六：按目录遍历知识库

```bash
curl -sS 'https://www.yuque.com/api/v2/repos/<book_id>/toc' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

用途：

- 读取知识库层级结构
- 构造遍历顺序
- 获取 `uuid / doc_id / parent_uuid / child_uuid`

## 目标七：搜索文档或知识库

搜索当前用户/团队范围：

```bash
curl -sS 'https://www.yuque.com/api/v2/search?q=AgentScope&type=doc' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

限定知识库范围：

```bash
curl -sS 'https://www.yuque.com/api/v2/search?q=AgentScope&type=doc&scope=<group_login>/<book_slug>' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

`scope` 规则：

- 搜团队内文档：`scope=group_a`
- 搜团队内知识库：`scope=group_a`
- 搜某个知识库内文档：`scope=group_a/book_x`

## 目标八：读取历史版本

先拿文档版本列表：

```bash
curl -sS 'https://www.yuque.com/api/v2/doc_versions?doc_id=<doc_id>' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

再拿某个版本详情：

```bash
curl -sS 'https://www.yuque.com/api/v2/doc_versions/<version_id>' \
  -H "X-Auth-Token: $YUQUE_TOKEN"
```

适用场景：

- 审计最近发布版本
- 对比正文变更
- 抓取已发布版本快照
