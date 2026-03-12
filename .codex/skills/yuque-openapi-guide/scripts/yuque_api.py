#!/usr/bin/env python3
"""Minimal Yuque OpenAPI client for repo/doc read workflows and generic calls."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any


DEFAULT_BASE_URL = "https://www.yuque.com"


def compact_dict(value: dict[str, Any]) -> dict[str, Any]:
    return {key: item for key, item in value.items() if item is not None}


def parse_body(text: str | None) -> Any:
    if text is None:
        return None
    if text == "-":
        return json.load(sys.stdin)
    return json.loads(text)


def resolve_token(cli_token: str | None) -> str:
    token = cli_token or os.environ.get("YUQUE_TOKEN")
    if token:
        return token
    raise SystemExit("Missing Yuque token. Pass --token or set YUQUE_TOKEN.")


def build_repo_prefix(args: argparse.Namespace) -> str:
    if args.book_id is not None:
        return f"/api/v2/repos/{args.book_id}"
    if args.group_login and args.book_slug:
        return f"/api/v2/repos/{args.group_login}/{args.book_slug}"
    raise SystemExit("Either --book-id or both --group-login and --book-slug are required.")


def call_api(
    *,
    base_url: str,
    token: str,
    method: str,
    path: str,
    query: dict[str, Any] | None = None,
    body: Any = None,
    timeout: float = 30.0,
) -> Any:
    params = compact_dict(query or {})
    url = f"{base_url.rstrip('/')}{path}"
    if params:
        url = f"{url}?{urllib.parse.urlencode(params, doseq=True)}"

    headers = {
        "Accept": "application/json",
        "X-Auth-Token": token,
    }
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")

    request = urllib.request.Request(url=url, data=data, method=method.upper(), headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        payload = exc.read().decode("utf-8", errors="replace")
        message = payload or exc.reason
        raise SystemExit(f"HTTP {exc.code}: {message}") from exc
    except urllib.error.URLError as exc:
        raise SystemExit(f"Network error: {exc.reason}") from exc


def print_json(data: Any, raw: bool) -> None:
    if raw and isinstance(data, str):
        print(data)
        return
    print(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=False))


def add_repo_selector(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--book-id", type=int, help="知识库 ID")
    parser.add_argument("--group-login", help="团队 login")
    parser.add_argument("--book-slug", help="知识库 slug")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Yuque OpenAPI helper")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="默认 https://www.yuque.com")
    parser.add_argument("--token", help="语雀 personal token；不传则读取 YUQUE_TOKEN")
    parser.add_argument("--raw", action="store_true", help="保留原始 JSON 输出格式")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("hello", help="心跳")
    subparsers.add_parser("me", help="当前用户信息")

    repos = subparsers.add_parser("repos", help="列个人或团队知识库")
    repos.add_argument("--owner-type", choices=["users", "groups"], default="users")
    repos.add_argument("--login", required=True, help="用户或团队 login")
    repos.add_argument("--offset", type=int, default=0)
    repos.add_argument("--limit", type=int, default=100)
    repos.add_argument("--type", choices=["Book", "Design"])
    repos.add_argument("--filter-by-ability", dest="filter_by_ability")

    docs = subparsers.add_parser("docs", help="列知识库文档")
    add_repo_selector(docs)
    docs.add_argument("--offset", type=int, default=0)
    docs.add_argument("--limit", type=int, default=100)
    docs.add_argument(
        "--optional-properties",
        default=None,
        help="如 hits,tags,latest_version_id",
    )

    doc = subparsers.add_parser("doc", help="获取文档详情")
    add_repo_selector(doc)
    doc.add_argument("--id", required=True, help="文档 ID 或 slug")
    doc.add_argument("--page-size", type=int)
    doc.add_argument("--page", type=int)

    toc = subparsers.add_parser("toc", help="获取知识库目录")
    add_repo_selector(toc)

    search = subparsers.add_parser("search", help="搜索文档或知识库")
    search.add_argument("--q", required=True)
    search.add_argument("--type", required=True, choices=["doc", "repo"])
    search.add_argument("--scope")
    search.add_argument("--page", type=int)
    search.add_argument("--offset", type=int)
    search.add_argument("--creator-id", dest="creatorId", type=int)
    search.add_argument("--creator")

    versions = subparsers.add_parser("versions", help="获取文档历史版本列表")
    versions.add_argument("--doc-id", required=True, type=int)

    version = subparsers.add_parser("version", help="获取单个历史版本详情")
    version.add_argument("--id", required=True, type=int, help="版本 ID")

    request = subparsers.add_parser("request", help="通用接口调用")
    request.add_argument("--method", required=True, choices=["GET", "POST", "PUT", "DELETE"])
    request.add_argument("--path", required=True, help="例如 /api/v2/user")
    request.add_argument("--query", help="JSON 格式 query，例如 '{\"limit\":100}'")
    request.add_argument("--body", help="JSON 字符串，或用 '-' 从 stdin 读取")

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    token = resolve_token(args.token)

    if args.command == "hello":
        result = call_api(base_url=args.base_url, token=token, method="GET", path="/api/v2/hello")
    elif args.command == "me":
        result = call_api(base_url=args.base_url, token=token, method="GET", path="/api/v2/user")
    elif args.command == "repos":
        path = f"/api/v2/{args.owner_type}/{args.login}/repos"
        result = call_api(
            base_url=args.base_url,
            token=token,
            method="GET",
            path=path,
            query={
                "offset": args.offset,
                "limit": args.limit,
                "type": args.type,
                "filterByAbility": args.filter_by_ability,
            },
        )
    elif args.command == "docs":
        path = f"{build_repo_prefix(args)}/docs"
        result = call_api(
            base_url=args.base_url,
            token=token,
            method="GET",
            path=path,
            query={
                "offset": args.offset,
                "limit": args.limit,
                "optional_properties": args.optional_properties,
            },
        )
    elif args.command == "doc":
        if args.book_id is None and not (args.group_login and args.book_slug):
            path = f"/api/v2/repos/docs/{args.id}"
        else:
            path = f"{build_repo_prefix(args)}/docs/{args.id}"
        result = call_api(
            base_url=args.base_url,
            token=token,
            method="GET",
            path=path,
            query={"page_size": args.page_size, "page": args.page},
        )
    elif args.command == "toc":
        result = call_api(
            base_url=args.base_url,
            token=token,
            method="GET",
            path=f"{build_repo_prefix(args)}/toc",
        )
    elif args.command == "search":
        result = call_api(
            base_url=args.base_url,
            token=token,
            method="GET",
            path="/api/v2/search",
            query={
                "q": args.q,
                "type": args.type,
                "scope": args.scope,
                "page": args.page,
                "offset": args.offset,
                "creatorId": args.creatorId,
                "creator": args.creator,
            },
        )
    elif args.command == "versions":
        result = call_api(
            base_url=args.base_url,
            token=token,
            method="GET",
            path="/api/v2/doc_versions",
            query={"doc_id": args.doc_id},
        )
    elif args.command == "version":
        result = call_api(
            base_url=args.base_url,
            token=token,
            method="GET",
            path=f"/api/v2/doc_versions/{args.id}",
        )
    else:
        query = json.loads(args.query) if args.query else None
        body = parse_body(args.body)
        result = call_api(
            base_url=args.base_url,
            token=token,
            method=args.method,
            path=args.path,
            query=query,
            body=body,
        )

    print_json(result, raw=args.raw)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
