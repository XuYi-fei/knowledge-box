# Scripts Usage (Runtime Import)

用于在**后端服务已启动**时，把语雀文档动态导入到 Knowledge Box 的审核流（`document_review_request`）。

## 前置条件

- 后端服务已启动（例如 `http://localhost:8080`）。
- 已有语雀 Token。
- 已有管理端账号（用于调用 `/api/admin/*` 接口）。

建议先设置环境变量：

```bash
export YUQUE_TOKEN=your_yuque_token
export KB_ADMIN_USERNAME=admin
export KB_ADMIN_PASSWORD=your_admin_password
export KB_BASE_URL=http://localhost:8080
```

## 单篇文档导入（推荐）

```bash
# 1) 导出语雀文档为 Markdown
python3 scripts/yuque_kb_migrate.py export-md \
  --book-id 70111390 \
  --doc-id 243374419 \
  --output-md tmp/yuque-batch/pilot/raw.md \
  --output-meta tmp/yuque-batch/pilot/meta.json

# 2) 图片转存到 Knowledge Box（走 paste-image 链路）
python3 scripts/yuque_kb_migrate.py rehost-images \
  --input-md tmp/yuque-batch/pilot/raw.md \
  --output-md tmp/yuque-batch/pilot/rehosted.md \
  --manifest-json tmp/yuque-batch/pilot/images.json \
  --kb-base-url "$KB_BASE_URL" \
  --kb-admin-username "$KB_ADMIN_USERNAME" \
  --kb-admin-password "$KB_ADMIN_PASSWORD" \
  --require-provider oss

# 3) 创建审核单（默认等待到 PENDING_REVIEW）
python3 scripts/yuque_kb_migrate.py init-review \
  --input-md tmp/yuque-batch/pilot/rehosted.md \
  --yuque-meta-json tmp/yuque-batch/pilot/meta.json \
  --source-filename yuque-70111390-243374419.md \
  --category-name "Spring AI Alibaba" \
  --column-name "Spring AI Alibaba" \
  --kb-base-url "$KB_BASE_URL" \
  --kb-admin-username "$KB_ADMIN_USERNAME" \
  --kb-admin-password "$KB_ADMIN_PASSWORD" \
  --output-json tmp/yuque-batch/pilot/init.json
```

## 批量导入（基于 docs-*.json）

示例：对 `tmp/yuque-batch/docs-70111390.json` 里的文档逐条执行同样流程。

```bash
python3 - <<'PY'
import json, os, subprocess, pathlib

book_id = 70111390
docs_file = pathlib.Path("tmp/yuque-batch/docs-70111390.json")
docs = json.loads(docs_file.read_text(encoding="utf-8"))["data"]

for d in docs:
    doc_id = d["id"]
    base = pathlib.Path(f"tmp/yuque-batch/runtime/{book_id}/{doc_id}")
    base.mkdir(parents=True, exist_ok=True)

    subprocess.run([
        "python3", "scripts/yuque_kb_migrate.py", "export-md",
        "--book-id", str(book_id),
        "--doc-id", str(doc_id),
        "--output-md", str(base / "raw.md"),
        "--output-meta", str(base / "meta.json"),
    ], check=True)

    subprocess.run([
        "python3", "scripts/yuque_kb_migrate.py", "rehost-images",
        "--input-md", str(base / "raw.md"),
        "--output-md", str(base / "rehosted.md"),
        "--manifest-json", str(base / "images.json"),
        "--kb-base-url", os.environ["KB_BASE_URL"],
        "--kb-admin-username", os.environ["KB_ADMIN_USERNAME"],
        "--kb-admin-password", os.environ["KB_ADMIN_PASSWORD"],
        "--require-provider", "oss",
    ], check=True)

    subprocess.run([
        "python3", "scripts/yuque_kb_migrate.py", "init-review",
        "--input-md", str(base / "rehosted.md"),
        "--yuque-meta-json", str(base / "meta.json"),
        "--source-filename", f"yuque-{book_id}-{doc_id}.md",
        "--category-name", "Spring AI Alibaba",
        "--column-name", "Spring AI Alibaba",
        "--kb-base-url", os.environ["KB_BASE_URL"],
        "--kb-admin-username", os.environ["KB_ADMIN_USERNAME"],
        "--kb-admin-password", os.environ["KB_ADMIN_PASSWORD"],
        "--output-json", str(base / "init.json"),
    ], check=True)
PY
```

## 说明

- `init-review` 是创建审核单，不会自动通过审核。
- `init-review` 支持通过 `--category-name` / `--column-name` 直接为审核单预填强制分类与专栏；后续管理员仍可在审核页调整。
- 若 payload 中带有 `importKey` / `yuqueSource` 等导入元数据，后端会同时按 `importKey + 正文内容指纹` 做重复导入拦截。
- 若要快速查看参数：`python3 scripts/yuque_kb_migrate.py <subcommand> --help`

## 清理卡住的 bootstrap 审核单

如果启动导入过程中后端被中断，`document_review_request` 可能停留在 `status=PROCESSING` 且 `stage=CHUNKING`，并因为 `importKey` 已存在而阻止后续重启继续导入。

可先 dry-run 预览，再确认删除：

```bash
python3 scripts/cleanup_stuck_bootstrap_reviews.py
python3 scripts/cleanup_stuck_bootstrap_reviews.py --apply
```

默认仅清理带 `importKey` 且前缀为 `yuque:` 的 `PROCESSING/CHUNKING` 审核单；删除 `document_review_request` 时，关联的 `document_review_chunk` / `document_review_asset` 会通过数据库级联一并删除。

## 清理重复正式文档

如果历史上已经把“不同 `importKey` 但正文完全相同”的文档发布进了 `knowledge_document`，可先 dry-run 预览，再确认执行清理：

```bash
python3 scripts/cleanup_duplicate_documents.py
python3 scripts/cleanup_duplicate_documents.py --apply
```

默认行为：

- 仅扫描 `visibility_type=PUBLIC` 且 `status=READY` 的正式文档。
- 按“分类 + 标题 + 正文 MD5 + 可见性 + 状态”分组。
- 每组默认保留最早的一条文档，其余重复文档会重挂审核单/ingestion 引用后再删除。
- 会同步删除重复文档的 chunk / asset / tag binding，并尝试清理 `public.kb_vector_store` 里的对应向量行。

常用参数：

```bash
python3 scripts/cleanup_duplicate_documents.py --keep newest --limit 10
python3 scripts/cleanup_duplicate_documents.py --skip-vector-delete --apply
python3 scripts/cleanup_duplicate_documents.py --vector-table public.kb_vector_store --apply
```

如果你跳过了向量删除，或者清理后仍担心检索残留，建议再执行一次全量索引重建。
