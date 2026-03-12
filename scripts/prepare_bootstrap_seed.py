#!/usr/bin/env python3
"""Prepare bootstrap seed item for startup import into document review flow."""

from __future__ import annotations

import argparse
import hashlib
import json
import time
from pathlib import Path
from typing import Any


def load_json_object(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise SystemExit(f"Expected JSON object in {path}")
    return payload


def derive_import_key(meta: dict[str, Any], markdown: str) -> str:
    book_id = meta.get("bookId")
    doc_id = meta.get("docId")
    if book_id is not None and doc_id is not None:
        return f"yuque:{book_id}:{doc_id}"
    digest = hashlib.sha256(markdown.encode("utf-8")).hexdigest()[:16]
    return f"markdown:{digest}"


def read_seed(seed_path: Path) -> list[dict[str, Any]]:
    if not seed_path.exists():
        return []
    payload = json.loads(seed_path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise SystemExit(f"Seed file must be JSON array: {seed_path}")
    for item in payload:
        if not isinstance(item, dict):
            raise SystemExit(f"Seed file item must be JSON object: {seed_path}")
    return payload


def write_seed(seed_path: Path, items: list[dict[str, Any]]) -> None:
    seed_path.parent.mkdir(parents=True, exist_ok=True)
    seed_path.write_text(json.dumps(items, ensure_ascii=False, indent=2), encoding="utf-8")


def merge_extension(
    import_key: str,
    yuque_meta: dict[str, Any] | None,
    extra_extension: dict[str, Any] | None,
) -> dict[str, Any]:
    extension: dict[str, Any] = {
        "importKey": import_key,
        "migration": {
            "tool": "scripts/prepare_bootstrap_seed.py",
            "preparedAtEpochMs": int(time.time() * 1000),
        },
    }
    if yuque_meta:
        extension["yuqueSource"] = {
            "bookId": yuque_meta.get("bookId"),
            "docId": yuque_meta.get("docId"),
            "docSlug": yuque_meta.get("docSlug"),
            "title": yuque_meta.get("title"),
            "namespace": yuque_meta.get("namespace"),
            "contentUpdatedAt": yuque_meta.get("contentUpdatedAt"),
            "docUrlPath": yuque_meta.get("docUrlPath"),
            "exportedBodyField": yuque_meta.get("exportedBodyField"),
        }
    if extra_extension:
        extension.update(extra_extension)
        extension["importKey"] = import_key
    return extension


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare seed JSON for startup bootstrap import.")
    parser.add_argument("--seed-json", default="backend/bootstrap/document-seed.json")
    parser.add_argument("--input-md", required=True)
    parser.add_argument("--title")
    parser.add_argument("--source-filename")
    parser.add_argument("--visibility-type", choices=["PUBLIC", "AGENT_ONLY"], default="PUBLIC")
    parser.add_argument("--import-key")
    parser.add_argument("--yuque-meta-json")
    parser.add_argument("--extra-extension-json")
    args = parser.parse_args()

    input_md_path = Path(args.input_md)
    if not input_md_path.exists():
        raise SystemExit(f"Markdown file not found: {input_md_path}")
    markdown = input_md_path.read_text(encoding="utf-8")

    yuque_meta = load_json_object(Path(args.yuque_meta_json)) if args.yuque_meta_json else None
    extra_extension = load_json_object(Path(args.extra_extension_json)) if args.extra_extension_json else None

    import_key = args.import_key or derive_import_key(yuque_meta or {}, markdown)
    title = args.title or (yuque_meta.get("title") if yuque_meta else None) or input_md_path.stem
    source_filename = args.source_filename or input_md_path.name
    extension_json = merge_extension(import_key, yuque_meta, extra_extension)

    seed_item = {
        "importKey": import_key,
        "title": title,
        "sourceFilename": source_filename,
        "visibilityType": args.visibility_type,
        "sourceMarkdownPath": str(input_md_path),
        "extensionJson": extension_json,
    }

    seed_path = Path(args.seed_json)
    seed_items = read_seed(seed_path)
    replaced = False
    for idx, existing in enumerate(seed_items):
        if str(existing.get("importKey")) == import_key:
            seed_items[idx] = seed_item
            replaced = True
            break
    if not replaced:
        seed_items.append(seed_item)
    write_seed(seed_path, seed_items)

    print(
        json.dumps(
            {
                "ok": True,
                "seedJson": str(seed_path),
                "importKey": import_key,
                "replaced": replaced,
                "itemCount": len(seed_items),
                "sourceMarkdownPath": str(input_md_path),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
