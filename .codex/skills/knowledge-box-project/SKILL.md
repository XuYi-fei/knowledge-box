---
name: knowledge-box-project
description: Repository workflow guardrails for the Knowledge Box project. Use when implementing, fixing, reviewing, testing, or documenting anything in this repo so Codex first reads the project rule file and progress file, follows local delivery constraints, and updates project progress after meaningful changes.
---

# Knowledge Box Project

Read the project rule file and progress file before doing substantial work in this repository. Use them as the source of truth for delivery constraints, recent pitfalls, and current feature status.

## Required Reads

1. Read `../../../rule.md`.
2. Read `../../../progress.md`.
3. Read only the repo files relevant to the current task.

Keep those two markdown files small. Do not paste them into new docs or duplicate their contents elsewhere unless the user asks.

## Working Loop

1. Build context from `rule.md`, `progress.md`, and the touched modules.
2. Implement the requested change while following the project constraints.
3. Run targeted verification when feasible.
4. If this task is a completed feature/bugfix and verification passed, stage related files and create a git commit before finishing.
5. Update `../../../progress.md` if the task changes project status, completed scope, or verification scope.
6. If the task delivers an independent feature, also sync a concise release note for the About tab by adding database data through an additive changelog/script against `about_release_note`.
7. If the task introduces a new recurring constraint or a new pitfall, add a short note to `../../../rule.md`.

## Update Rules

- Update `progress.md` after meaningful feature work, bug fixes, infrastructure changes, or verification changes.
- For completed feature/bugfix work, do not stop at code + tests: create a git commit in the same turn after verification passes.
- Keep `progress.md` factual and compressed. Preserve only current stage, core completed capabilities, verified scope, and the next focus.
- Separate "completed" from "verified".
- When an independent feature ships, do not stop at `progress.md`: also append a concise About-tab release note through an additive database change, rather than hiding the change only in docs.
- Update `rule.md` only for stable project rules, repeat mistakes, or setup pitfalls worth preserving.
- Keep both files concise so they remain cheap to load every turn.

## What To Preserve

- Prefer explicit configuration over hardcoded secrets, accounts, hostnames, and environment-specific constants.
- Preserve the distinction between local profile config and shared base config.
- Treat `application-local.yml` as user-owned local environment state. Do not overwrite existing local values unless the user explicitly asks for that file to be edited.
- When configuration examples or shared defaults need updates, prefer `application.yml`, `application-local.yml.example`, README, or new config keys over rewriting the user's local file.
- Keep README, local config examples, and progress status aligned with shipped behavior.
- Keep the About tab aligned with shipped behavior when a new independent feature is delivered.
- Do not mark a feature as verified unless you actually ran tests or completed a direct validation path.
