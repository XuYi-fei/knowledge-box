#!/usr/bin/env python3
"""Preview or clean duplicate knowledge documents grouped by content fingerprint."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


JDBC_POSTGRESQL_RE = re.compile(
    r"^jdbc:postgresql://(?P<host>[^/:?#]+)(?::(?P<port>\d+))?/(?P<database>[^?]+)"
)
IDENTIFIER_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
QUALIFIED_IDENTIFIER_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)?$")


@dataclass
class DbConfig:
    host: str
    port: str
    database: str
    username: str
    password: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Preview or delete duplicate knowledge_document rows grouped by category + title + "
            "source_markdown fingerprint + visibility + status. The script keeps one canonical "
            "document per group and rewires dependent rows before deleting duplicates."
        )
    )
    parser.add_argument(
        "--env-file",
        default="config/knowledge-box.env",
        help="Path to the deployment env file that contains DB_URL / DB_USERNAME / DB_PASSWORD.",
    )
    parser.add_argument(
        "--visibility-type",
        default="PUBLIC",
        help="Only scan this knowledge_document.visibility_type. Default: PUBLIC",
    )
    parser.add_argument(
        "--status",
        default="READY",
        help="Only scan this knowledge_document.status. Default: READY",
    )
    parser.add_argument(
        "--keep",
        choices=["oldest", "newest"],
        default="oldest",
        help="Which document to keep inside a duplicate group. Default: oldest",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Optional max number of duplicate rows to delete. Default: 0 (no limit).",
    )
    parser.add_argument(
        "--vector-table",
        default="public.kb_vector_store",
        help="Qualified pgvector table used by Spring AI. Default: public.kb_vector_store",
    )
    parser.add_argument(
        "--skip-vector-delete",
        action="store_true",
        help="Skip deleting vector rows for duplicate chunks. Use this only if you will rebuild vectors immediately.",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Actually apply the cleanup. Without this flag the script only prints a preview.",
    )
    return parser.parse_args()


def load_env_file(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise FileNotFoundError(f"Env file not found: {path}")

    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key:
            continue
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        values[key] = value
    return values


def parse_db_config(env_values: dict[str, str]) -> DbConfig:
    db_url = env_values.get("DB_URL", "").strip()
    db_username = env_values.get("DB_USERNAME", "").strip()
    db_password = env_values.get("DB_PASSWORD", "")
    if not db_url or not db_username:
        raise ValueError("DB_URL / DB_USERNAME must be present in the env file.")

    match = JDBC_POSTGRESQL_RE.match(db_url)
    if not match:
        raise ValueError(f"Unsupported DB_URL format: {db_url}")

    return DbConfig(
        host=match.group("host"),
        port=match.group("port") or "5432",
        database=match.group("database"),
        username=db_username,
        password=db_password,
    )


def run_psql(db: DbConfig, sql: str) -> str:
    env = os.environ.copy()
    env["PGPASSWORD"] = db.password
    result = subprocess.run(
        [
            "psql",
            "-X",
            "-v",
            "ON_ERROR_STOP=1",
            "-t",
            "-h",
            db.host,
            "-p",
            db.port,
            "-U",
            db.username,
            "-d",
            db.database,
            "-A",
            "-F",
            "\t",
            "-q",
            "-c",
            sql,
        ],
        check=True,
        capture_output=True,
        text=True,
        env=env,
    )
    return result.stdout.strip()


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def quote_identifier(value: str) -> str:
    parts = value.split(".")
    if not QUALIFIED_IDENTIFIER_RE.fullmatch(value) or any(not IDENTIFIER_RE.fullmatch(part) for part in parts):
        raise ValueError(f"Unsafe SQL identifier: {value}")
    return ".".join('"' + part + '"' for part in parts)


def duplicate_targets_cte(args: argparse.Namespace) -> str:
    order_direction = "asc" if args.keep == "oldest" else "desc"
    limit_clause = f"\n    limit {args.limit}" if args.limit > 0 else ""
    return f"""
with ranked as (
    select
        kd.id,
        kd.category_id,
        coalesce(dc.name, '') as category_name,
        kd.title,
        kd.visibility_type,
        kd.status,
        md5(coalesce(kd.source_markdown, '')) as content_fingerprint,
        coalesce(kd.extension_json, '{{}}')::jsonb ->> 'importKey' as import_key,
        row_number() over (
            partition by kd.category_id, lower(btrim(coalesce(kd.title, ''))), md5(coalesce(kd.source_markdown, '')), kd.visibility_type, kd.status
            order by kd.id {order_direction}
        ) as row_no,
        first_value(kd.id) over (
            partition by kd.category_id, lower(btrim(coalesce(kd.title, ''))), md5(coalesce(kd.source_markdown, '')), kd.visibility_type, kd.status
            order by kd.id {order_direction}
        ) as keep_id,
        count(*) over (
            partition by kd.category_id, lower(btrim(coalesce(kd.title, ''))), md5(coalesce(kd.source_markdown, '')), kd.visibility_type, kd.status
        ) as group_size
    from knowledge_document kd
    left join document_category dc on dc.id = kd.category_id
    where kd.visibility_type = {sql_literal(args.visibility_type)}
      and kd.status = {sql_literal(args.status)}
),
targets as (
    select *
    from ranked
    where group_size > 1
      and row_no > 1
    order by category_name asc, title asc, id asc{limit_clause}
)
""".strip()


def preview_groups(db: DbConfig, args: argparse.Namespace) -> list[str]:
    sql = (
        duplicate_targets_cte(args)
        + "\nselect\n"
        + "    t.keep_id,\n"
        + "    t.id as delete_id,\n"
        + "    t.category_name,\n"
        + "    t.title,\n"
        + "    t.content_fingerprint,\n"
        + "    coalesce(t.import_key, '') as import_key,\n"
        + "    (select count(*) from document_chunk c where c.document_id = t.id) as chunk_count,\n"
        + "    (select count(*) from document_asset a where a.document_id = t.id) as asset_count,\n"
        + "    (select count(*) from document_tag_binding b where b.document_id = t.id) as tag_count,\n"
        + "    (select count(*) from document_review_request r where r.source_document_id = t.id) as source_review_refs,\n"
        + "    (select count(*) from document_review_request r where r.published_document_id = t.id) as published_review_refs,\n"
        + "    (select count(*) from ingestion_job j where j.document_id = t.id) as ingestion_refs\n"
        + "from targets t;"
    )
    output = run_psql(db, sql)
    return [line for line in output.splitlines() if line.strip()]


def cleanup_duplicates(db: DbConfig, args: argparse.Namespace) -> list[str]:
    vector_delete_cte = ""
    vector_delete_summary = "0 as vector_rows_deleted"
    if not args.skip_vector_delete:
        vector_table = quote_identifier(args.vector_table)
        vector_delete_cte = f"""
vector_deleted as (
    delete from {vector_table} v
    using duplicate_chunks c
    where v.id = 'chunk-' || c.id::text
    returning v.id
),
"""
        vector_delete_summary = "(select count(*) from vector_deleted) as vector_rows_deleted"

    sql = (
        duplicate_targets_cte(args)
        + """
,
duplicate_chunks as (
    select c.id, c.document_id
    from document_chunk c
    join targets t on t.id = c.document_id
),
merged_tag_bindings as (
    insert into document_tag_binding (document_id, tag_id)
    select distinct t.keep_id, b.tag_id
    from document_tag_binding b
    join targets t on t.id = b.document_id
    on conflict (document_id, tag_id) do nothing
    returning id
),
updated_source_reviews as (
    update document_review_request r
    set source_document_id = t.keep_id,
        updated_at = now()
    from targets t
    where r.source_document_id = t.id
    returning r.id
),
updated_published_reviews as (
    update document_review_request r
    set published_document_id = t.keep_id,
        updated_at = now()
    from targets t
    where r.published_document_id = t.id
    returning r.id
),
updated_ingestion_jobs as (
    update ingestion_job j
    set document_id = t.keep_id,
        updated_at = now()
    from targets t
    where j.document_id = t.id
    returning j.id
),
deleted_tag_bindings as (
    delete from document_tag_binding b
    using targets t
    where b.document_id = t.id
    returning b.id
),
deleted_assets as (
    delete from document_asset a
    using targets t
    where a.document_id = t.id
    returning a.id
),\n
"""
        + vector_delete_cte
        + """
deleted_chunks as (
    delete from document_chunk c
    using targets t
    where c.document_id = t.id
    returning c.id
),
refreshed_keeper_tags as (
    update knowledge_document kd
    set tags = coalesce(
        (
            select json_agg(dt.name order by lower(dt.name))::text
            from document_tag_binding b
            join document_tag dt on dt.id = b.tag_id
            where b.document_id = kd.id
        ),
        '[]'
    ),
    updated_at = now()
    where kd.id in (select distinct keep_id from targets)
    returning kd.id
),
deleted_documents as (
    delete from knowledge_document kd
    using targets t
    where kd.id = t.id
    returning kd.id, kd.title
)
select
    (select count(*) from targets) as duplicate_documents_deleted,
    (select count(*) from merged_tag_bindings) as merged_tag_bindings,
    (select count(*) from updated_source_reviews) as rewired_source_reviews,
    (select count(*) from updated_published_reviews) as rewired_published_reviews,
    (select count(*) from updated_ingestion_jobs) as rewired_ingestion_jobs,
    (select count(*) from deleted_tag_bindings) as deleted_tag_bindings,
    (select count(*) from deleted_assets) as deleted_assets,
    (select count(*) from deleted_chunks) as deleted_chunks,
    """
        + vector_delete_summary
        + ",\n"
        + "    (select count(*) from refreshed_keeper_tags) as refreshed_keeper_tags,\n"
        + "    (select count(*) from deleted_documents) as deleted_documents;"
    )
    output = run_psql(db, sql)
    return [line for line in output.splitlines() if line.strip()]


def main() -> int:
    args = parse_args()
    env_file = Path(args.env_file)
    env_values = load_env_file(env_file)
    db = parse_db_config(env_values)

    preview = preview_groups(db, args)
    print(
        f"[cleanup-duplicate-documents] preview duplicate_rows={len(preview)} "
        f"visibilityType={args.visibility_type} status={args.status} keep={args.keep} "
        f"limit={args.limit or 'ALL'} vectorDelete={'OFF' if args.skip_vector_delete else args.vector_table}"
    )

    if preview:
        print(
            "keep_id\tdelete_id\tcategory_name\ttitle\tcontent_fingerprint\timport_key\tchunk_count\tasset_count\ttag_count\tsource_review_refs\tpublished_review_refs\tingestion_refs"
        )
        for line in preview:
            print(line)
    else:
        print("[cleanup-duplicate-documents] no duplicate documents matched the current filter.")

    if not args.apply:
        print("[cleanup-duplicate-documents] dry-run only. Re-run with --apply to execute cleanup.")
        return 0

    summary = cleanup_duplicates(db, args)
    print("[cleanup-duplicate-documents] cleanup summary")
    print(
        "duplicate_documents_deleted\tmerged_tag_bindings\trewired_source_reviews\trewired_published_reviews\trewired_ingestion_jobs\tdeleted_tag_bindings\tdeleted_assets\tdeleted_chunks\tvector_rows_deleted\trefreshed_keeper_tags\tdeleted_documents"
    )
    for line in summary:
        print(line)
    print("[cleanup-duplicate-documents] cleanup finished. If retrieval results still look stale, run a full index rebuild once.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        sys.stderr.write(exc.stderr or str(exc) + "\n")
        raise
