# Scan Map

Use this file to keep architecture updates targeted.

## Repository Level

- `../../../README.md`
- `../../../rule.md`
- `../../../progress.md`
- `../../../pom.xml`
- `../../../architecture.md`

## Frontend

- `../../../frontend/package.json`
- `../../../frontend/.env.example`
- `../../../frontend/vite.config.ts`
- `../../../frontend/src/main.tsx`
- `../../../frontend/src/app/router.tsx`
- `../../../frontend/src/lib/api.ts`
- `../../../frontend/src/lib/auth.ts`
- `../../../frontend/src/lib/sse.ts`
- `../../../frontend/src/lib/types.ts`
- `../../../frontend/src/features/auth/*`
- `../../../frontend/src/features/chat/*`
- `../../../frontend/src/features/admin/*`
- `../../../frontend/src/layouts/*`

## Backend Core

- `../../../backend/pom.xml`
- `../../../backend/src/main/resources/application.yml`
- `../../../backend/src/main/resources/application-local.yml.example`
- `../../../backend/src/main/resources/db/changelog/*`
- `../../../backend/src/main/java/com/knowledgebox/KnowledgeBoxApplication.java`
- `../../../backend/src/main/java/com/knowledgebox/config/*`
- `../../../backend/src/main/java/com/knowledgebox/security/*`
- `../../../backend/src/main/java/com/knowledgebox/web/admin/*`
- `../../../backend/src/main/java/com/knowledgebox/web/publicapi/*`
- `../../../backend/src/main/java/com/knowledgebox/service/admin/*`
- `../../../backend/src/main/java/com/knowledgebox/service/auth/*`
- `../../../backend/src/main/java/com/knowledgebox/service/chat/*`
- `../../../backend/src/main/java/com/knowledgebox/service/document/*`
- `../../../backend/src/main/java/com/knowledgebox/repository/*`
- `../../../backend/src/main/java/com/knowledgebox/domain/*`

## Maintenance Notes

- If `com.knowledgebox.app.*` still has no active files, call it out as empty or inactive instead of inventing architecture around it.
- For frontend endpoint mapping, use `frontend/src/lib/api.ts` as the source of truth.
- For backend config keys, use `KnowledgeBoxProperties` plus `application.yml` and `application-local.yml.example` as the source of truth.
- For LLM and RAG chain, prioritize `ChatOrchestrator`, `KnowledgeBaseSearchTool`, `KnowledgeBaseRetrievalService`, `KnowledgeBaseIndexingService`, and related controllers.
