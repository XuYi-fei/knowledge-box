# Document Governance Progress

## 当前阶段

- 文档治理主链路已落地，当前重点是继续细化审核策略、失败可观测性和导入运维闭环。

## 已完成

- 已落地文档上传、审核流、分类/标签、Markdown 预览编辑、图片转存、向量写入、索引重建与 bootstrap 初始化导入。
- 已新增用户侧“知识入库工作台”首版：登录用户可上传 Markdown / 文本型 PDF 或直接粘贴内容，系统会保留原始文件、生成待确认草稿，并在确认后创建 `PENDING_REVIEW` 审核单复用现有治理链路。
- 已补齐大 PDF 异步入库链路：当文本型 PDF 超过页数或体积阈值时，上传会自动切到异步任务；系统会保留原始 PDF、规划多个子文档、边生成边创建真实 `PENDING_REVIEW` 审核单，并允许中途取消后续生成。
- 用户侧知识入库与治理衔接体验已继续补齐：草稿确认提交后可直接跳转到审核页，分类支持临时新建，草稿/子文档中的超链接在预览区会以蓝色样式高亮。
- 大 PDF 文本提取阶段现已支持逐页进度反馈；任务详情会持续展示“正在读取第 X/Y 页”以及当前页文本片段，便于观察超大 PDF 的解析推进情况。
- 知识入库任务现已支持任务链路清理：仅对已结束任务开放删除，删除时会清理任务记录、阶段/子文档记录与原始源文件，但会保留已生成的待审核单继续流转。
- 管理端文档审核已支持批量审核通过。
- 文档导入与审核已支持“专栏”能力；bootstrap 与运行时 `init-review` 可指定分类/专栏，审核页也可编辑专栏。
- 导入判重已升级为“双重判定”：除 `importKey` 幂等外，还会按正文内容指纹拦截跨来源重复内容。
- 已提供 `scripts/cleanup_duplicate_documents.py` 与后台“重复治理”页，用于预览和清理历史重复正式文档。
- 已提供 `scripts/cleanup_stuck_bootstrap_reviews.py`，可清理卡在 `PROCESSING/CHUNKING` 的 bootstrap 审核单。

## 已验证范围

- 前端：`npm --prefix frontend run build` 可通过，已覆盖文档审核页、批量审核、专栏编辑与重复治理页面。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖用户侧知识入库页、工作区路由与导航接线。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 可通过，已覆盖审核、专栏、重复治理与导入链路。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeIngestionSourceToolTests,KnowledgeIngestionServiceTests test` 可通过，已覆盖知识入库 source tool、草稿分析状态流转与确认入审核单链路。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeIngestionServiceTests,KnowledgeIngestionTaskServiceTests test` 可通过，已覆盖大 PDF 自动分流、异步任务拆解、任务取消保留已产出审核单，以及 Liquibase/服务层编译回归。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests package` 与 `java -jar backend/backend-app/target/knowledge-box-backend-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=18082` 可通过，已验证知识入库 064/065 Liquibase 迁移在本地库上可正常执行启动。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖 `/ingest` 自动分流上传、`/ingest/tasks/:taskId` 任务页、阶段列表、子文档预览与取消按钮编译回归。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖 `/ingest/tasks` 任务中心、草稿确认后跳审核页按钮、分类临时新建与 Markdown 链接高亮回归。
- 后端：`mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeIngestionTaskServiceTests test` 可通过，已覆盖大 PDF 任务文本提取、拆解生成与取消链路回归。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖任务页显示逐页 PDF 读取摘要、当前页片段预览与 1 秒轮询回归。
- 后端：`mvn -q -pl backend/backend-app -am -DskipTests compile` 与 `mvn -q -pl backend/backend-app -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeIngestionTaskServiceTests test` 可通过，已覆盖知识入库任务删除接口、源文件删除抽象与“保留待审核单”的服务层回归。
- 前端：`npm --prefix frontend run build` 可通过，已覆盖任务中心/详情页的删除任务入口、查看源文件入口与删除后列表刷新/跳转回归。
- 后端：`mvn -q -pl backend/backend-app -am -Dtest=DocumentBootstrapImportRunnerTests -Dsurefire.failIfNoSpecifiedTests=false test` 可通过，已验证 bootstrap seed 会带入 `categoryName/columnName`。
- 脚本：`python3 scripts/cleanup_duplicate_documents.py --help` 与 `python3 scripts/cleanup_stuck_bootstrap_reviews.py --help` 可执行。

## 待继续推进

- 继续细化审核权限颗粒度、审核原因规范化与失败可观测性。
- 持续清理和治理历史导入遗留数据，降低重复内容和卡单对运营的影响。
- 继续补齐用户侧知识入库在真实 OSS、模型和审核运营场景下的联调，评估 PDF OCR 与入口 Agent/Tool 配置化是否需要进一步落地。
- 继续补齐超大 PDF 在线联调体验，例如任务列表筛选、失败重试、批量管理与更细粒度的阶段提示。
- 补齐真实模型、邮件和对象存储参与下的完整审核运维闭环。

## 关键注意点

- 审核/生成异步线程要在 `afterCommit` 后启动，避免子线程读不到未提交数据。
- Liquibase 已执行过的初始化 changeSet 不能直接改；像知识入库这类新增表、字段或 about 更新都必须走增量 changelog。
- 当前项目的用户表名是 `user_account`；新增文档治理/用户侧入库表若需要引用用户外键，不能误写成不存在的 `app_user`。
- 大 PDF 异步任务不要依赖请求期 `MultipartFile` 生命周期；后台生成阶段应通过存储层 `read(objectKey)` 重新读取已保留的原始 PDF。
- 标签绑定写入前要去重；删旧绑定优先 bulk delete 或显式 flush，避免唯一键冲突。
- DashScope embedding 单批上限按 `10` 控制。
- bootstrap 导入不能只依赖 `importKey`；当来源系统会生成不同 `importKey` 时，还要结合正文内容指纹判重。
- 若 bootstrap 审核单卡在 `PROCESSING/CHUNKING`，`importKey` 会继续占用幂等键，需先恢复或清理卡单。
