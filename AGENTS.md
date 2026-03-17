# Knowledge Box Repo Notes

- Use the local skill at `.codex/skills/knowledge-box-project` for implementation, bugfix, review, test, and documentation work in this repository.
- Before substantial work, read `rule.md` and `progress.md`.
- For module-specific work, also read and update the matching `docs/progress/<module>/progress.md`.
- Keep root `progress.md` as the project index/summary; put module details in the module progress files.
- Git 提交默认使用中文“简短标题 + 详细正文”；正文尽量完整说明对应 bug/优化/功能、问题背景和解决办法。
- 每次提交后都要回到对应模块 progress 检查并补齐当前进度；若进度因此变更，也要继续提交这些文档更新。
- Add new stable project constraints or repeat pitfalls to `rule.md`, but keep it concise.
- Treat `application-local.yml` as user-owned local config; do not rewrite existing values unless explicitly requested.
