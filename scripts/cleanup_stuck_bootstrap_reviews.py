#!/usr/bin/env python3
"""Clean up bootstrap-imported review requests stuck in PROCESSING/CHUNKING."""

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
            "Delete bootstrap-imported document review requests that are stuck in a "
            "specific status/stage pair. Child review assets/chunks are removed by "
            "database cascade."
        )
    )
    parser.add_argument(
        "--env-file",
        default="config/knowledge-box.env",
        help="Path to the deployment env file that contains DB_URL / DB_USERNAME / DB_PASSWORD.",
    )
    parser.add_argument(
        "--status",
        default="PROCESSING",
        help="Target document_review_request.status value. Default: PROCESSING",
    )
    parser.add_argument(
        "--stage",
        default="CHUNKING",
        help="Target document_review_request.stage value. Default: CHUNKING",
    )
    parser.add_argument(
        "--import-key-prefix",
        default="yuque:",
        help="Only delete rows whose extension_json.importKey starts with this prefix. Default: yuque:",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Optional max number of rows to delete, ordered by updated_at desc. Default: 0 (no limit).",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Actually delete rows. Without this flag the script only prints a preview.",
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


def build_filter_clause(args: argparse.Namespace) -> str:
    conditions = [
        f"status = {sql_literal(args.status)}",
        f"stage = {sql_literal(args.stage)}",
        "coalesce(extension_json, '{}')::jsonb ? 'importKey'",
    ]
    if args.import_key_prefix:
        conditions.append(
            "coalesce(extension_json, '{}')::jsonb ->> 'importKey' like "
            + sql_literal(args.import_key_prefix + "%")
        )
    return " and ".join(conditions)


def build_targets_cte(filter_clause: str, limit: int) -> str:
    limit_clause = f"\n    limit {limit}" if limit > 0 else ""
    return (
        "with targets as (\n"
        "    select id,\n"
        "           title,\n"
        "           request_code,\n"
        "           status,\n"
        "           stage,\n"
        "           progress_percent,\n"
        "           updated_at,\n"
        "           coalesce(extension_json, '{}')::jsonb ->> 'importKey' as import_key\n"
        "    from document_review_request\n"
        f"    where {filter_clause}\n"
        "    order by updated_at desc, id desc"
        f"{limit_clause}\n"
        ")\n"
    )


def preview_rows(db: DbConfig, args: argparse.Namespace) -> list[str]:
    filter_clause = build_filter_clause(args)
    sql = (
        build_targets_cte(filter_clause, args.limit)
        + "select t.id,\n"
        + "       t.title,\n"
        + "       t.status,\n"
        + "       t.stage,\n"
        + "       t.progress_percent,\n"
        + "       t.updated_at,\n"
        + "       t.import_key,\n"
        + "       (select count(*) from document_review_chunk c where c.review_request_id = t.id) as chunk_count,\n"
        + "       (select count(*) from document_review_asset a where a.review_request_id = t.id) as asset_count\n"
        + "from targets t;"
    )
    output = run_psql(db, sql)
    return [line for line in output.splitlines() if line.strip()]


def delete_rows(db: DbConfig, args: argparse.Namespace) -> list[str]:
    filter_clause = build_filter_clause(args)
    sql = (
        build_targets_cte(filter_clause, args.limit)
        + "delete from document_review_request d\n"
        + "using targets t\n"
        + "where d.id = t.id\n"
        + "returning t.id, t.title, t.request_code, t.import_key;"
    )
    output = run_psql(db, sql)
    return [line for line in output.splitlines() if line.strip()]


def main() -> int:
    args = parse_args()
    env_file = Path(args.env_file)
    env_values = load_env_file(env_file)
    db = parse_db_config(env_values)

    preview = preview_rows(db, args)
    print(
        f"[cleanup-stuck-bootstrap-reviews] target preview count={len(preview)} "
        f"status={args.status} stage={args.stage} importKeyPrefix={args.import_key_prefix or '*'} "
        f"limit={args.limit or 'ALL'}"
    )

    if preview:
        print("id\ttitle\tstatus\tstage\tprogress_percent\tupdated_at\timport_key\tchunk_count\tasset_count")
        for line in preview:
            print(line)
    else:
        print("[cleanup-stuck-bootstrap-reviews] no matching rows found.")

    if not args.apply:
        print("[cleanup-stuck-bootstrap-reviews] dry-run only. Re-run with --apply to delete these rows.")
        return 0

    deleted = delete_rows(db, args)
    print(f"[cleanup-stuck-bootstrap-reviews] deleted rows={len(deleted)}")
    if deleted:
        print("id\ttitle\trequest_code\timport_key")
        for line in deleted:
            print(line)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        sys.stderr.write(exc.stderr or str(exc) + "\n")
        raise
