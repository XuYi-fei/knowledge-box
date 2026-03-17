#!/usr/bin/env python3
"""Migrate a Yuque document into Knowledge Box review flow.

Workflow:
1) export-md: export one Yuque doc into local Markdown + metadata JSON.
2) rehost-images: download image links in Markdown and upload via KB paste-image API.
3) init-review: create KB review request from the migrated Markdown.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import mimetypes
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DEFAULT_YUQUE_BASE_URL = "https://www.yuque.com"
DEFAULT_KB_BASE_URL = "http://localhost:8080"

MARKDOWN_IMAGE_PATTERN = re.compile(
    r"!\[(?P<alt>[^\]]*)\]\((?P<url><[^>]+>|[^)\s]+)(?P<tail>\s+\"[^\"]*\")?\)"
)
HTML_IMAGE_PATTERN = re.compile(
    r'(<img\b[^>]*?\bsrc=["\'])(?P<url>[^"\']+)(["\'][^>]*>)',
    flags=re.IGNORECASE,
)


def ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def write_text(path: Path, content: str) -> None:
    ensure_parent(path)
    path.write_text(content, encoding="utf-8")


def write_json(path: Path, payload: Any) -> None:
    ensure_parent(path)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def resolve_token(cli_token: str | None, env_name: str) -> str:
    token = cli_token or os.getenv(env_name)
    if token:
        return token
    raise SystemExit(f"Missing token: pass --token or set {env_name}")


def resolve_required(value: str | None, env_name: str, arg_name: str) -> str:
    resolved = value or os.getenv(env_name)
    if resolved:
        return resolved
    raise SystemExit(f"Missing {arg_name}: pass {arg_name} or set {env_name}")


def sanitize_file_name(name: str) -> str:
    lowered = re.sub(r"\s+", "-", name.strip())
    cleaned = re.sub(r"[^0-9A-Za-z._\-\u4e00-\u9fff]+", "-", lowered)
    cleaned = re.sub(r"-{2,}", "-", cleaned).strip("-.")
    return cleaned or "yuque-doc"


def compact_dict(value: dict[str, Any]) -> dict[str, Any]:
    return {k: v for k, v in value.items() if v is not None}


def request_json(
    *,
    method: str,
    url: str,
    headers: dict[str, str] | None = None,
    query: dict[str, Any] | None = None,
    body: Any = None,
    timeout: float = 60.0,
) -> Any:
    params = compact_dict(query or {})
    if params:
        url = f"{url}?{urllib.parse.urlencode(params, doseq=True)}"
    data: bytes | None = None
    req_headers = {"Accept": "application/json"}
    if headers:
        req_headers.update(headers)
    if body is not None:
        req_headers["Content-Type"] = "application/json"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url=url, method=method.upper(), headers=req_headers, data=data)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            text = resp.read().decode("utf-8")
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as exc:
        payload = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code} {url}: {payload or exc.reason}") from exc
    except urllib.error.URLError as exc:
        raise SystemExit(f"Network error {url}: {exc.reason}") from exc


def request_bytes(
    *,
    method: str,
    url: str,
    headers: dict[str, str] | None = None,
    timeout: float = 60.0,
) -> tuple[bytes, str | None]:
    req_headers = {"User-Agent": "knowledge-box-yuque-migrator/1.0"}
    if headers:
        req_headers.update(headers)
    req = urllib.request.Request(url=url, method=method.upper(), headers=req_headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.read(), resp.headers.get("Content-Type")
    except urllib.error.HTTPError as exc:
        payload = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} while downloading {url}: {payload or exc.reason}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Network error while downloading {url}: {exc.reason}") from exc


def build_repo_prefix(args: argparse.Namespace) -> str:
    if args.book_id is not None:
        return f"/api/v2/repos/{args.book_id}"
    if args.group_login and args.book_slug:
        return f"/api/v2/repos/{args.group_login}/{args.book_slug}"
    raise SystemExit("Either --book-id or both --group-login and --book-slug are required.")


def fetch_yuque_doc(args: argparse.Namespace) -> dict[str, Any]:
    path = f"{build_repo_prefix(args)}/docs/{args.doc_id}"
    payload = request_json(
        method="GET",
        url=f"{args.yuque_base_url.rstrip('/')}{path}",
        headers={"X-Auth-Token": args.token},
    )
    data = payload.get("data")
    if not isinstance(data, dict):
        raise SystemExit("Yuque API response missing data object.")
    return data


def choose_doc_content(doc: dict[str, Any], prefer_field: str) -> tuple[str, str]:
    candidates = [prefer_field, "body", "body_draft", "body_lake", "body_html"]
    seen: set[str] = set()
    for field in candidates:
        if field in seen:
            continue
        seen.add(field)
        value = doc.get(field)
        if isinstance(value, str) and value.strip():
            return value, field
    raise SystemExit("No non-empty body field found in doc detail.")


def cmd_export_md(args: argparse.Namespace) -> int:
    doc = fetch_yuque_doc(args)
    content, field = choose_doc_content(doc, args.prefer_field)
    title = str(doc.get("title") or "yuque-doc")
    base_name = sanitize_file_name(f"{title}-{doc.get('id', 'doc')}")
    output_md = Path(args.output_md) if args.output_md else Path("tmp/yuque-exports") / f"{base_name}.raw.md"
    output_meta = (
        Path(args.output_meta)
        if args.output_meta
        else output_md.with_suffix(".meta.json")
    )

    write_text(output_md, content)
    metadata = {
        "source": "yuque",
        "bookId": doc.get("book_id"),
        "docId": doc.get("id"),
        "docSlug": doc.get("slug"),
        "title": title,
        "format": doc.get("format"),
        "namespace": ((doc.get("book") or {}).get("namespace") if isinstance(doc.get("book"), dict) else None),
        "contentUpdatedAt": doc.get("content_updated_at"),
        "updatedAt": doc.get("updated_at"),
        "public": doc.get("public"),
        "preferredBodyField": args.prefer_field,
        "exportedBodyField": field,
        "docUrlPath": f"/{(doc.get('user') or {}).get('login', '')}/{(doc.get('book') or {}).get('slug', '')}/{doc.get('slug', '')}",
        "exportedAtEpochMs": int(time.time() * 1000),
        "outputMarkdownPath": str(output_md),
    }
    write_json(output_meta, metadata)
    print(
        json.dumps(
            {
                "ok": True,
                "markdownPath": str(output_md),
                "metadataPath": str(output_meta),
                "title": title,
                "docId": doc.get("id"),
                "bookId": doc.get("book_id"),
                "bodyField": field,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


def is_http_url(url: str) -> bool:
    lower = url.lower()
    return lower.startswith("http://") or lower.startswith("https://")


def strip_angle_brackets(url: str) -> str:
    if url.startswith("<") and url.endswith(">"):
        return url[1:-1]
    return url


def extract_image_urls(markdown: str) -> list[str]:
    urls: list[str] = []
    seen: set[str] = set()
    for match in MARKDOWN_IMAGE_PATTERN.finditer(markdown):
        raw = strip_angle_brackets(match.group("url").strip())
        if is_http_url(raw) and raw not in seen:
            seen.add(raw)
            urls.append(raw)
    for match in HTML_IMAGE_PATTERN.finditer(markdown):
        raw = match.group("url").strip()
        if is_http_url(raw) and raw not in seen:
            seen.add(raw)
            urls.append(raw)
    return urls


def guess_filename(url: str, content_type: str | None) -> str:
    parsed = urllib.parse.urlparse(url)
    name = Path(parsed.path).name
    if not name or "." not in name:
        ext = mimetypes.guess_extension((content_type or "").split(";")[0].strip().lower()) or ".bin"
        return f"image-{uuid.uuid4().hex}{ext}"
    return name


def make_multipart_body(field_name: str, filename: str, content: bytes, content_type: str | None) -> tuple[bytes, str]:
    boundary = f"----kb-migrator-{uuid.uuid4().hex}"
    ctype = content_type or "application/octet-stream"
    lines = [
        f"--{boundary}",
        f'Content-Disposition: form-data; name="{field_name}"; filename="{filename}"',
        f"Content-Type: {ctype}",
        "",
    ]
    head = "\r\n".join(lines).encode("utf-8") + b"\r\n"
    tail = f"\r\n--{boundary}--\r\n".encode("utf-8")
    return head + content + tail, boundary


def kb_basic_auth_header(username: str, password: str) -> str:
    token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def kb_upload_image(
    *,
    kb_base_url: str,
    username: str,
    password: str,
    image_bytes: bytes,
    filename: str,
    content_type: str | None,
    timeout: float,
) -> dict[str, Any]:
    body, boundary = make_multipart_body("image", filename, image_bytes, content_type)
    req = urllib.request.Request(
        url=f"{kb_base_url.rstrip('/')}/api/admin/documents/paste-image",
        method="POST",
        headers={
            "Authorization": kb_basic_auth_header(username, password),
            "Accept": "application/json",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
        data=body,
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            text = resp.read().decode("utf-8")
            return json.loads(text)
    except urllib.error.HTTPError as exc:
        payload = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} while uploading image: {payload or exc.reason}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Network error while uploading image: {exc.reason}") from exc


def replace_urls_in_markdown(markdown: str, mapping: dict[str, str]) -> str:
    def replace_md(match: re.Match[str]) -> str:
        alt = match.group("alt")
        raw_url = match.group("url")
        tail = match.group("tail") or ""
        normalized = strip_angle_brackets(raw_url.strip())
        target = mapping.get(normalized, normalized)
        return f"![{alt}]({target}{tail})"

    updated = MARKDOWN_IMAGE_PATTERN.sub(replace_md, markdown)

    def replace_html(match: re.Match[str]) -> str:
        prefix, raw_url, suffix = match.group(1), match.group("url"), match.group(3)
        target = mapping.get(raw_url.strip(), raw_url.strip())
        return f"{prefix}{target}{suffix}"

    return HTML_IMAGE_PATTERN.sub(replace_html, updated)


@dataclass
class RehostResult:
    original_url: str
    new_url: str
    provider: str
    object_key: str | None
    md5: str
    content_type: str | None
    content_length: int | None


def cmd_rehost_images(args: argparse.Namespace) -> int:
    input_path = Path(args.input_md)
    if not input_path.exists():
        raise SystemExit(f"Input markdown not found: {input_path}")
    markdown = input_path.read_text(encoding="utf-8")

    kb_user = resolve_required(args.kb_admin_username, "KB_ADMIN_USERNAME", "--kb-admin-username")
    kb_pass = resolve_required(args.kb_admin_password, "KB_ADMIN_PASSWORD", "--kb-admin-password")

    urls = extract_image_urls(markdown)
    replacements: dict[str, str] = {}
    migrated: list[RehostResult] = []
    failures: list[dict[str, str]] = []
    cache: dict[str, dict[str, Any]] = {}

    for idx, url in enumerate(urls, start=1):
        try:
            if url in cache:
                uploaded = cache[url]
            else:
                image_bytes, content_type = request_bytes(method="GET", url=url, timeout=args.timeout_seconds)
                filename = guess_filename(url, content_type)
                uploaded = kb_upload_image(
                    kb_base_url=args.kb_base_url,
                    username=kb_user,
                    password=kb_pass,
                    image_bytes=image_bytes,
                    filename=filename,
                    content_type=content_type,
                    timeout=args.timeout_seconds,
                )
                cache[url] = uploaded

            provider = str(uploaded.get("provider") or "")
            if args.require_provider and provider != args.require_provider:
                raise RuntimeError(
                    f"Provider mismatch for {url}: expected={args.require_provider}, actual={provider or '<empty>'}"
                )
            new_url = str(uploaded.get("url") or "")
            if not new_url:
                raise RuntimeError(f"No url returned for image: {url}")
            replacements[url] = new_url
            migrated.append(
                RehostResult(
                    original_url=url,
                    new_url=new_url,
                    provider=provider,
                    object_key=uploaded.get("objectKey"),
                    md5=str(uploaded.get("md5") or ""),
                    content_type=uploaded.get("contentType"),
                    content_length=uploaded.get("contentLength"),
                )
            )
            print(f"[{idx}/{len(urls)}] rehosted: {url} -> {new_url}")
        except Exception as exc:  # noqa: BLE001
            failures.append({"url": url, "error": str(exc)})
            print(f"[{idx}/{len(urls)}] failed: {url} ({exc})", file=sys.stderr)
            if not args.allow_image_failures:
                break

    if failures and not args.allow_image_failures:
        manifest_path = Path(args.manifest_json) if args.manifest_json else input_path.with_suffix(".images.json")
        write_json(
            manifest_path,
            {
                "ok": False,
                "inputMarkdownPath": str(input_path),
                "failedCount": len(failures),
                "migratedCount": len(migrated),
                "failures": failures,
                "migrated": [result.__dict__ for result in migrated],
            },
        )
        raise SystemExit(f"Image rehost failed, manifest written: {manifest_path}")

    updated_markdown = replace_urls_in_markdown(markdown, replacements)
    output_md = Path(args.output_md) if args.output_md else input_path.with_suffix(".rehosted.md")
    manifest_path = Path(args.manifest_json) if args.manifest_json else input_path.with_suffix(".images.json")
    write_text(output_md, updated_markdown)
    write_json(
        manifest_path,
        {
            "ok": len(failures) == 0,
            "inputMarkdownPath": str(input_path),
            "outputMarkdownPath": str(output_md),
            "migratedCount": len(migrated),
            "failedCount": len(failures),
            "requireProvider": args.require_provider,
            "migrated": [result.__dict__ for result in migrated],
            "failures": failures,
        },
    )
    print(
        json.dumps(
            {
                "ok": len(failures) == 0,
                "outputMarkdownPath": str(output_md),
                "manifestPath": str(manifest_path),
                "migratedCount": len(migrated),
                "failedCount": len(failures),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


def merge_extension_json(
    *,
    extension_json_file: str | None,
    yuque_meta_json: str | None,
) -> dict[str, Any]:
    extension: dict[str, Any] = {}
    if extension_json_file:
        payload = json.loads(Path(extension_json_file).read_text(encoding="utf-8"))
        if not isinstance(payload, dict):
            raise SystemExit("extension json file must contain a JSON object")
        extension.update(payload)
    if yuque_meta_json:
        yuque_meta_payload = json.loads(Path(yuque_meta_json).read_text(encoding="utf-8"))
        if not isinstance(yuque_meta_payload, dict):
            raise SystemExit("yuque meta json must contain a JSON object")
        book_id = yuque_meta_payload.get("bookId")
        doc_id = yuque_meta_payload.get("docId")
        if book_id is not None and doc_id is not None:
            extension["importKey"] = f"yuque:{book_id}:{doc_id}"
        extension["yuqueSource"] = {
            "bookId": yuque_meta_payload.get("bookId"),
            "docId": yuque_meta_payload.get("docId"),
            "docSlug": yuque_meta_payload.get("docSlug"),
            "title": yuque_meta_payload.get("title"),
            "namespace": yuque_meta_payload.get("namespace"),
            "contentUpdatedAt": yuque_meta_payload.get("contentUpdatedAt"),
            "docUrlPath": yuque_meta_payload.get("docUrlPath"),
            "exportedBodyField": yuque_meta_payload.get("exportedBodyField"),
        }
    extension["migration"] = {
        "tool": "scripts/yuque_kb_migrate.py",
        "timestampEpochMs": int(time.time() * 1000),
    }
    return extension


def kb_json_request(
    *,
    method: str,
    kb_base_url: str,
    path: str,
    username: str,
    password: str,
    body: Any | None = None,
    timeout: float = 60.0,
) -> Any:
    headers = {
        "Authorization": kb_basic_auth_header(username, password),
        "Accept": "application/json",
    }
    return request_json(
        method=method,
        url=f"{kb_base_url.rstrip('/')}{path}",
        headers=headers,
        body=body,
        timeout=timeout,
    )


def cmd_init_review(args: argparse.Namespace) -> int:
    input_md = Path(args.input_md)
    if not input_md.exists():
        raise SystemExit(f"Input markdown not found: {input_md}")
    markdown = input_md.read_text(encoding="utf-8")
    title = args.title or input_md.stem
    source_filename = args.source_filename or input_md.name

    kb_user = resolve_required(args.kb_admin_username, "KB_ADMIN_USERNAME", "--kb-admin-username")
    kb_pass = resolve_required(args.kb_admin_password, "KB_ADMIN_PASSWORD", "--kb-admin-password")

    extension = merge_extension_json(
        extension_json_file=args.extension_json_file,
        yuque_meta_json=args.yuque_meta_json,
    )
    extension["contentFingerprint"] = hashlib.md5(markdown.encode("utf-8")).hexdigest()
    payload = {
        "title": title,
        "sourceFilename": source_filename,
        "visibilityType": args.visibility_type,
        "sourceMarkdown": markdown,
        "extensionJson": json.dumps(extension, ensure_ascii=False),
        "selectedCategoryName": args.category_name,
        "selectedColumnName": args.column_name,
    }
    created = kb_json_request(
        method="POST",
        kb_base_url=args.kb_base_url,
        path="/api/admin/documents/upload-json",
        username=kb_user,
        password=kb_pass,
        body=payload,
        timeout=args.timeout_seconds,
    )
    review_request_id = created.get("reviewRequestId")
    review_request_code = created.get("reviewRequestCode")
    if review_request_id is None:
        raise SystemExit("upload-json did not return reviewRequestId")

    result: dict[str, Any] = {
        "created": created,
        "reviewRequestId": review_request_id,
        "reviewRequestCode": review_request_code,
        "targetStatus": args.wait_status,
    }

    if args.no_wait:
        if args.output_json:
            write_json(Path(args.output_json), result)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0

    deadline = time.time() + args.wait_timeout_seconds
    last_status = None
    detail: dict[str, Any] | None = None
    terminal_statuses = {"FAILED", "REJECTED", "APPROVED"}
    while time.time() <= deadline:
        detail = kb_json_request(
            method="GET",
            kb_base_url=args.kb_base_url,
            path=f"/api/admin/document-reviews/{review_request_id}",
            username=kb_user,
            password=kb_pass,
            timeout=args.timeout_seconds,
        )
        status = str(detail.get("status"))
        if status != last_status:
            print(f"review[{review_request_id}] status={status}")
            last_status = status
        if status == args.wait_status:
            result["finalStatus"] = status
            result["reviewDetail"] = detail
            if args.output_json:
                write_json(Path(args.output_json), result)
            print(json.dumps(result, ensure_ascii=False, indent=2))
            return 0
        if status in terminal_statuses and status != args.wait_status:
            result["finalStatus"] = status
            result["reviewDetail"] = detail
            if args.output_json:
                write_json(Path(args.output_json), result)
            raise SystemExit(f"Review ended with status={status}, expected {args.wait_status}")
        time.sleep(args.poll_interval_seconds)

    result["finalStatus"] = last_status
    result["reviewDetail"] = detail
    if args.output_json:
        write_json(Path(args.output_json), result)
    raise SystemExit(
        f"Timeout waiting review {review_request_id} to reach {args.wait_status}; lastStatus={last_status}"
    )


def add_repo_selector(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--book-id", type=int, help="Yuque repo id")
    parser.add_argument("--group-login", help="Yuque group login")
    parser.add_argument("--book-slug", help="Yuque repo slug")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Migrate one Yuque doc into Knowledge Box review flow.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    export_md = subparsers.add_parser("export-md", help="Export one Yuque doc to local Markdown")
    export_md.add_argument("--yuque-base-url", default=DEFAULT_YUQUE_BASE_URL)
    export_md.add_argument("--token", help="Yuque token; fallback env: YUQUE_TOKEN")
    add_repo_selector(export_md)
    export_md.add_argument("--doc-id", required=True, help="Yuque doc id or slug")
    export_md.add_argument(
        "--prefer-field",
        default="body",
        choices=["body", "body_draft", "body_lake", "body_html"],
        help="Preferred text field in doc detail",
    )
    export_md.add_argument("--output-md", help="Exported markdown path")
    export_md.add_argument("--output-meta", help="Exported metadata json path")

    rehost = subparsers.add_parser("rehost-images", help="Rehost markdown image URLs via KB paste-image API")
    rehost.add_argument("--input-md", required=True)
    rehost.add_argument("--output-md")
    rehost.add_argument("--manifest-json")
    rehost.add_argument("--kb-base-url", default=DEFAULT_KB_BASE_URL)
    rehost.add_argument("--kb-admin-username", help="fallback env: KB_ADMIN_USERNAME")
    rehost.add_argument("--kb-admin-password", help="fallback env: KB_ADMIN_PASSWORD")
    rehost.add_argument("--timeout-seconds", type=float, default=60.0)
    rehost.add_argument("--allow-image-failures", action="store_true")
    rehost.add_argument(
        "--require-provider",
        choices=["oss", "local"],
        default="oss",
        help="Fail if paste-image returns provider mismatch",
    )

    init_review = subparsers.add_parser("init-review", help="Create KB document review request from markdown")
    init_review.add_argument("--input-md", required=True)
    init_review.add_argument("--title")
    init_review.add_argument("--source-filename")
    init_review.add_argument("--visibility-type", default="PUBLIC", choices=["PUBLIC", "AGENT_ONLY"])
    init_review.add_argument("--category-name", help="Force selected category name for the created review")
    init_review.add_argument("--column-name", help="Assign the created review/document to a named column")
    init_review.add_argument("--yuque-meta-json", help="metadata json generated by export-md")
    init_review.add_argument("--extension-json-file", help="extra extension json object file")
    init_review.add_argument("--kb-base-url", default=DEFAULT_KB_BASE_URL)
    init_review.add_argument("--kb-admin-username", help="fallback env: KB_ADMIN_USERNAME")
    init_review.add_argument("--kb-admin-password", help="fallback env: KB_ADMIN_PASSWORD")
    init_review.add_argument("--wait-status", default="PENDING_REVIEW")
    init_review.add_argument("--wait-timeout-seconds", type=int, default=600)
    init_review.add_argument("--poll-interval-seconds", type=float, default=2.0)
    init_review.add_argument("--timeout-seconds", type=float, default=60.0)
    init_review.add_argument("--no-wait", action="store_true")
    init_review.add_argument("--output-json", help="Persist init result json")

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    if args.command == "export-md":
        args.token = resolve_token(args.token, "YUQUE_TOKEN")
        return cmd_export_md(args)
    if args.command == "rehost-images":
        return cmd_rehost_images(args)
    if args.command == "init-review":
        return cmd_init_review(args)

    raise SystemExit(f"Unknown command: {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
