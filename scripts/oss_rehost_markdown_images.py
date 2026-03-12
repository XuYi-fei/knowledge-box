#!/usr/bin/env python3
"""Rehost Markdown/HTML image URLs to Alibaba OSS and rewrite the markdown file."""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

try:
    import oss2
except ImportError as exc:  # pragma: no cover - runtime dependency check
    raise SystemExit("Missing dependency `oss2`. Install with: pip install oss2") from exc


MARKDOWN_IMAGE_PATTERN = re.compile(
    r"!\[(?P<alt>[^\]]*)\]\((?P<url><[^>]+>|[^)\s]+)(?P<tail>\s+\"[^\"]*\")?\)"
)
HTML_IMAGE_PATTERN = re.compile(
    r'(<img\b[^>]*?\bsrc=["\'])(?P<url>[^"\']+)(["\'][^>]*>)',
    flags=re.IGNORECASE,
)


def strip_angle_brackets(url: str) -> str:
    if url.startswith("<") and url.endswith(">"):
        return url[1:-1]
    return url


def is_http_url(url: str) -> bool:
    lower = url.lower()
    return lower.startswith("http://") or lower.startswith("https://")


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


def request_bytes(url: str, timeout: float) -> tuple[bytes, str | None]:
    req = urllib.request.Request(url=url, method="GET", headers={"User-Agent": "knowledge-box-oss-rehost/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.read(), resp.headers.get("Content-Type")
    except urllib.error.HTTPError as exc:
        payload = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} for {url}: {payload or exc.reason}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Network error for {url}: {exc.reason}") from exc


def guess_extension(url: str, content_type: str | None) -> str:
    parsed = urllib.parse.urlparse(url)
    suffix = Path(parsed.path).suffix.lower()
    if suffix:
        return suffix.lstrip(".")
    guessed = mimetypes.guess_extension((content_type or "").split(";")[0].strip().lower()) or ".bin"
    return guessed.lstrip(".")


def normalize_path_prefix(prefix: str) -> str:
    return prefix.strip().strip("/")


def build_object_key(path_prefix: str, md5_value: str, ext: str) -> str:
    normalized_prefix = normalize_path_prefix(path_prefix)
    base = f"assets/{md5_value}.{ext}"
    if normalized_prefix:
        return f"{normalized_prefix}/{base}"
    return base


def resolve_public_url(public_base_url: str | None, bucket: str, endpoint: str, object_key: str) -> str:
    encoded_key = urllib.parse.quote(object_key, safe="/")
    if public_base_url and public_base_url.strip():
        return public_base_url.rstrip("/") + "/" + encoded_key
    host = endpoint.replace("https://", "").replace("http://", "")
    return f"https://{bucket}.{host}/{encoded_key}"


def upload_to_oss(
    *,
    endpoint: str,
    bucket_name: str,
    access_key_id: str,
    access_key_secret: str,
    object_key: str,
    content: bytes,
    content_type: str | None,
) -> None:
    auth = oss2.Auth(access_key_id, access_key_secret)
    bucket = oss2.Bucket(auth, endpoint, bucket_name)
    if bucket.object_exists(object_key):
        return
    headers = {}
    if content_type:
        headers["Content-Type"] = content_type
    bucket.put_object(object_key, content, headers=headers)


def replace_urls(markdown: str, replacements: dict[str, str]) -> str:
    def replace_md(match: re.Match[str]) -> str:
        alt = match.group("alt")
        tail = match.group("tail") or ""
        raw_url = strip_angle_brackets(match.group("url").strip())
        target = replacements.get(raw_url, raw_url)
        return f"![{alt}]({target}{tail})"

    updated = MARKDOWN_IMAGE_PATTERN.sub(replace_md, markdown)

    def replace_html(match: re.Match[str]) -> str:
        prefix, raw_url, suffix = match.group(1), match.group("url"), match.group(3)
        target = replacements.get(raw_url.strip(), raw_url.strip())
        return f"{prefix}{target}{suffix}"

    return HTML_IMAGE_PATTERN.sub(replace_html, updated)


def resolve_required(value: str | None, env_name: str, arg_name: str) -> str:
    resolved = value or os.getenv(env_name)
    if resolved:
        return resolved
    raise SystemExit(f"Missing {arg_name}: pass {arg_name} or set {env_name}")


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Rehost markdown images to Alibaba OSS.")
    parser.add_argument("--input-md", required=True)
    parser.add_argument("--output-md")
    parser.add_argument("--manifest-json")
    parser.add_argument("--timeout-seconds", type=float, default=60.0)
    parser.add_argument("--allow-failures", action="store_true")
    parser.add_argument("--endpoint", help="fallback env: KB_STORAGE_OSS_ENDPOINT")
    parser.add_argument("--bucket", help="fallback env: KB_STORAGE_OSS_BUCKET")
    parser.add_argument("--access-key-id", help="fallback env: KB_STORAGE_OSS_ACCESS_KEY_ID")
    parser.add_argument("--access-key-secret", help="fallback env: KB_STORAGE_OSS_ACCESS_KEY_SECRET")
    parser.add_argument("--public-base-url", help="fallback env: KB_STORAGE_OSS_PUBLIC_BASE_URL")
    parser.add_argument(
        "--path-prefix",
        default=None,
        help="fallback env: KB_STORAGE_OSS_PATH_PREFIX (default: knowledge-box)",
    )
    args = parser.parse_args()

    input_path = Path(args.input_md)
    if not input_path.exists():
        raise SystemExit(f"Input markdown not found: {input_path}")
    output_path = Path(args.output_md) if args.output_md else input_path.with_suffix(".rehosted.md")
    manifest_path = Path(args.manifest_json) if args.manifest_json else input_path.with_suffix(".oss-images.json")

    endpoint = resolve_required(args.endpoint, "KB_STORAGE_OSS_ENDPOINT", "--endpoint")
    bucket = resolve_required(args.bucket, "KB_STORAGE_OSS_BUCKET", "--bucket")
    access_key_id = resolve_required(args.access_key_id, "KB_STORAGE_OSS_ACCESS_KEY_ID", "--access-key-id")
    access_key_secret = resolve_required(args.access_key_secret, "KB_STORAGE_OSS_ACCESS_KEY_SECRET", "--access-key-secret")
    public_base_url = args.public_base_url if args.public_base_url is not None else os.getenv("KB_STORAGE_OSS_PUBLIC_BASE_URL")
    path_prefix = args.path_prefix if args.path_prefix is not None else os.getenv("KB_STORAGE_OSS_PATH_PREFIX", "knowledge-box")

    markdown = input_path.read_text(encoding="utf-8")
    image_urls = extract_image_urls(markdown)
    replacements: dict[str, str] = {}
    migrated: list[dict[str, Any]] = []
    failures: list[dict[str, str]] = []
    uploaded_by_md5: dict[str, str] = {}

    for index, url in enumerate(image_urls, start=1):
        try:
            content, content_type = request_bytes(url, timeout=args.timeout_seconds)
            md5_value = hashlib.md5(content).hexdigest()
            ext = guess_extension(url, content_type)
            object_key = build_object_key(path_prefix, md5_value, ext)
            if md5_value not in uploaded_by_md5:
                upload_to_oss(
                    endpoint=endpoint,
                    bucket_name=bucket,
                    access_key_id=access_key_id,
                    access_key_secret=access_key_secret,
                    object_key=object_key,
                    content=content,
                    content_type=content_type,
                )
                uploaded_by_md5[md5_value] = object_key
            else:
                object_key = uploaded_by_md5[md5_value]
            new_url = resolve_public_url(public_base_url, bucket, endpoint, object_key)
            replacements[url] = new_url
            migrated.append(
                {
                    "sourceUrl": url,
                    "targetUrl": new_url,
                    "objectKey": object_key,
                    "md5": md5_value,
                    "contentType": content_type,
                    "contentLength": len(content),
                }
            )
            print(f"[{index}/{len(image_urls)}] rehosted: {url} -> {new_url}")
        except Exception as exc:  # noqa: BLE001
            failures.append({"url": url, "error": str(exc)})
            print(f"[{index}/{len(image_urls)}] failed: {url} ({exc})", file=sys.stderr)
            if not args.allow_failures:
                break

    if failures and not args.allow_failures:
        write_json(
            manifest_path,
            {
                "ok": False,
                "inputMarkdownPath": str(input_path),
                "migratedCount": len(migrated),
                "failedCount": len(failures),
                "migrated": migrated,
                "failures": failures,
            },
        )
        raise SystemExit(f"Rehost failed. Manifest: {manifest_path}")

    rewritten = replace_urls(markdown, replacements)
    write_text(output_path, rewritten)
    write_json(
        manifest_path,
        {
            "ok": len(failures) == 0,
            "inputMarkdownPath": str(input_path),
            "outputMarkdownPath": str(output_path),
            "migratedCount": len(migrated),
            "failedCount": len(failures),
            "migrated": migrated,
            "failures": failures,
        },
    )
    print(
        json.dumps(
            {
                "ok": len(failures) == 0,
                "outputMarkdownPath": str(output_path),
                "manifestPath": str(manifest_path),
                "migratedCount": len(migrated),
                "failedCount": len(failures),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
