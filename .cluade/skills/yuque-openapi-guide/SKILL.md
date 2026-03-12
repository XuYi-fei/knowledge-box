---
name: yuque-openapi-guide
description: Quick guide for Yuque OpenAPI based on the local yuque_openapi HTML doc. Use when users ask to read personal Yuque docs or knowledge bases, list repos, fetch docs, inspect TOC or history, search content, or operate Yuque repos/docs through the official API.
---

# Yuque OpenAPI Guide

基于仓库中的本地文档 `yuque_openapi_20251121_green.html` 总结语雀 OpenAPI，优先服务“读取个人语雀文档与知识库”的场景。

## 适用场景

- 用户提到“语雀 / Yuque / yuque openapi / 语雀接口 / 语雀知识库 / 语雀文档”。
- 需要列出个人或团队知识库、按路径读取文档、获取目录、获取历史版本、做站内搜索。
- 需要判断该用 `login`、`group_login`、`book_id`、`book_slug`、`doc id` 还是 `doc slug`。
- 用户明确要求创建、更新、删除知识库或文档。

## 工作方式

1. 先读 [references/overview.md](references/overview.md)，确认鉴权方式、路径标识和安全边界。
2. 如果目标是“读取个人语雀内容”，优先读 [references/read-workflows.md](references/read-workflows.md)。
3. 只按需加载主题文件，不要把整个 `references/` 文件夹全部读入上下文。
4. 默认走只读链路；除非用户明确要求修改，否则不要建议 `POST/PUT/DELETE`。
5. 回答时优先给出：
   - 应调用的接口
   - 路径参数和查询参数
   - 关键返回字段
   - 读取/同步时的注意事项

## 主题导航

- 鉴权、标识、错误码、公共约定：[references/overview.md](references/overview.md)
- 读取个人文档/知识库的推荐链路：[references/read-workflows.md](references/read-workflows.md)
- 脚本使用说明：[references/script-usage.md](references/script-usage.md)
- 用户与搜索接口：[references/user-search.md](references/user-search.md)
- 知识库接口：[references/repos.md](references/repos.md)
- 文档与历史版本接口：[references/docs.md](references/docs.md)
- 目录接口：[references/toc.md](references/toc.md)
- 团队成员与统计接口：[references/group-statistics.md](references/group-statistics.md)

## 输出要求

- 默认用中文回答。
- 优先给最短可执行链路，不先做大而全铺陈。
- 如需真正发请求，优先使用 `scripts/yuque_api.py`，不要现写临时脚本。
- 不要把真实 token 写进 skill 文件或仓库源码；优先通过 `YUQUE_TOKEN` 或命令行参数临时注入。
- 当用户只想“读内容”时，优先推荐 `GET /user -> /users/{login}/repos -> /repos/.../docs -> /repos/.../docs/{id}`。
- 当用户要“按知识库结构遍历”时，补充 `GET /repos/.../toc`。
- 当用户要“找文档”但不知道 repo/doc 标识时，优先推荐 `GET /search` 或先列 repo/docs。
- 当用户要修改目录时，提醒“创建文档不会自动进目录”且“删除目录节点不会删除关联文档”。
