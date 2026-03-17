# Document Governance Progress

## 当前阶段

- 文档治理主链路已落地，当前重点是继续细化审核策略、失败可观测性和导入运维闭环。

## 已完成

- 已落地文档上传、审核流、分类/标签、Markdown 预览编辑、图片转存、向量写入、索引重建与 bootstrap 初始化导入。
- 管理端文档审核已支持批量审核通过。
- 文档导入与审核已支持“专栏”能力；bootstrap 与运行时 `init-review` 可指定分类/专栏，审核页也可编辑专栏。
- 导入判重已升级为“双重判定”：除 `importKey` 幂等外，还会按正文内容指纹拦截跨来源重复内容。
- 已提供 `scripts/cleanup_duplicate_documents.py` 与后台“重复治理”页，用于预览和清理历史重复正式文档。
- 已提供 `scripts/cleanup_stuck_bootstrap_reviews.py`，可清理卡在 `PROCESSING/CHUNKING` 的 bootstrap 审核单。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖文档审核页、批量审核、专栏编辑与重复治理页面。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 可通过，已覆盖审核、专栏、重复治理与导入链路。
- 后端：`mvn -q -pl backend/backend-app -am -Dtest=DocumentBootstrapImportRunnerTests -Dsurefire.failIfNoSpecifiedTests=false test` 可通过，已验证 bootstrap seed 会带入 `categoryName/columnName`。
- 脚本：`python3 scripts/cleanup_duplicate_documents.py --help` 与 `python3 scripts/cleanup_stuck_bootstrap_reviews.py --help` 可执行。

## 待继续推进

- 继续细化审核权限颗粒度、审核原因规范化与失败可观测性。
- 持续清理和治理历史导入遗留数据，降低重复内容和卡单对运营的影响。
- 补齐真实模型、邮件和对象存储参与下的完整审核运维闭环。

## 关键注意点

- 审核/生成异步线程要在 `afterCommit` 后启动，避免子线程读不到未提交数据。
- 标签绑定写入前要去重；删旧绑定优先 bulk delete 或显式 flush，避免唯一键冲突。
- DashScope embedding 单批上限按 `10` 控制。
- bootstrap 导入不能只依赖 `importKey`；当来源系统会生成不同 `importKey` 时，还要结合正文内容指纹判重。
- 若 bootstrap 审核单卡在 `PROCESSING/CHUNKING`，`importKey` 会继续占用幂等键，需先恢复或清理卡单。
