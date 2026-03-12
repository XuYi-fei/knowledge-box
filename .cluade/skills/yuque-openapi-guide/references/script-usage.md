# 访问脚本

脚本位置：

- `scripts/yuque_api.py`

## 安全约定

- 不要把真实 personal token 写进仓库文件。
- 优先通过环境变量 `YUQUE_TOKEN` 注入。
- 如需临时调试，也可以用 `--token` 显式传入。

## 常用命令

查看当前用户：

```bash
YUQUE_TOKEN='<token>' python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py me
```

列个人知识库：

```bash
YUQUE_TOKEN='<token>' python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py repos \
  --owner-type users \
  --login your-login \
  --limit 100
```

列某个知识库文档：

```bash
YUQUE_TOKEN='<token>' python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py docs \
  --book-id 123456 \
  --optional-properties hits,tags,latest_version_id
```

按 slug 读取团队知识库中的文档：

```bash
YUQUE_TOKEN='<token>' python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py doc \
  --group-login team-login \
  --book-slug handbook \
  --id getting-started
```

读取目录：

```bash
YUQUE_TOKEN='<token>' python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py toc \
  --group-login team-login \
  --book-slug handbook
```

搜索：

```bash
YUQUE_TOKEN='<token>' python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py search \
  --q AgentScope \
  --type doc \
  --scope team-login/handbook
```

历史版本：

```bash
YUQUE_TOKEN='<token>' python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py versions \
  --doc-id 123456
```

## 通用接口调用

脚本保留 `request` 子命令，给 skill 未拆成专用命令的接口兜底：

```bash
YUQUE_TOKEN='<token>' python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py request \
  --method GET \
  --path /api/v2/users/your-login/groups
```

带 query：

```bash
YUQUE_TOKEN='<token>' python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py request \
  --method GET \
  --path /api/v2/search \
  --query '{"q":"AgentScope","type":"doc","scope":"team-login/handbook"}'
```

带 JSON body：

```bash
YUQUE_TOKEN='<token>' python3 .codex/skills/yuque-openapi-guide/scripts/yuque_api.py request \
  --method POST \
  --path /api/v2/users/your-login/repos \
  --body '{"name":"知识库名称","slug":"kb-slug"}'
```

## 脚本覆盖范围

内建子命令：

- `hello`
- `me`
- `repos`
- `docs`
- `doc`
- `toc`
- `search`
- `versions`
- `version`
- `request`

## 注意事项

- `doc` 子命令如果不传 repo 选择参数，会自动走 `GET /api/v2/repos/docs/{id}`。
- `docs` / `doc` / `toc` 要么传 `--book-id`，要么同时传 `--group-login` 和 `--book-slug`。
- `request --body -` 支持从标准输入读取 JSON。
