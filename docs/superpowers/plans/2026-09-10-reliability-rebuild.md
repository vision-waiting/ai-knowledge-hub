# AI Knowledge Hub Reliability Rebuild

**Goal:** Complete the document-to-answer workflow against the supplied product specification, prioritizing completeness and stability as confirmed by the user.

**Architecture:** Retain Vue 3, TypeScript, Element Plus, Spring Boot 3, Java 21, MyBatis-Plus, MySQL, Redis, and Qdrant. Repair existing modules in place and keep explicit local development AI adapters. Persist business state in MySQL, vectors in Qdrant, files in local storage, and use Redis for request limits. Preserve the user's pending directory renames and other edits.

**Execution:** Apply systematic debugging, focused regression tests, and independently scoped parallel implementation tasks. Do not commit the existing staged work. Verify every subsystem and then exercise the integrated application.

## Acceptance Contract

- Login, current profile, password changes, user administration, and current database roles/status enforced on every authenticated request.
- Create/search/update/delete knowledge bases; deletion clears files, chunks, tags, vectors, sessions, and messages.
- Upload PDF/DOCX/PPTX/TXT/Markdown asynchronously after transaction commit; processing status is visible, failures actionable, repeated indexing idempotent, active jobs cannot race destructive operations.
- Initialize Qdrant collection with actual embedding dimensions, validate configuration, filter by knowledge base, and clean up vectors on deletion.
- Stream upstream AI tokens incrementally; surface provider errors truthfully; stop generation; persist answers, modes and citations; restore individual sessions; reject foreign or mismatched session IDs.
- Refuse unsupported questions without irrelevant citations. Keep local demo mode explicit. Use actual data for sidebar sessions, processing statuses, statistics and runtime indicators.
- Frontend navigation and administration usable at desktop and mobile widths; citation preview/download authenticated.
- One-command startup reads environment configuration without embedded credentials. Docker deployment includes backend, frontend and infrastructure; document any unavailable external prerequisites precisely.

## Tasks

- [x] Inspect source, specification, configuration, pending changes and available runtimes.
- [x] Document pipeline: fix transaction/event boundaries, processing/reindex/delete behavior, Qdrant initialization, parser edge cases, document detail API and focused regression tests.
- [x] Chat backend: real streaming, history/citation persistence, cancellation, errors, question/session validation, retrieval quality and focused regression tests.
- [x] Frontend: real sessions, chat isolation, resilient SSE client, citations, documents, users/profile, runtime data and mobile layouts; run Vitest and production build.
- [x] Platform: user administration/password update/revocation, Redis request limits, scoped real statistics, environment-based startup and full Docker deployment.
- [x] Integration: full Maven and Vitest suites, production builds, infrastructure/API smoke tests, browser desktop/mobile workflow verification, write final startup and validation documentation.

## Validation Record (2026-09-11)

- `mvn test`: 70 tests, 0 failures, 0 errors.
- `npm run test -- --run`: 18 tests, 0 failures.
- `npm run build`: production bundle generated successfully.
- `docker compose config --quiet`: valid configuration.
- `scripts/smoke-test.mjs`: upload, asynchronous processing, download, reindex, Qdrant retrieval, streamed answer, citations, follow-up history, refusal, session deletion and cleanup verified against MySQL/Redis/Qdrant.
- Browser login and dashboard workflow verified at `http://127.0.0.1:5174`; responsive mobile CSS is covered by the `max-width: 800px` and `max-width: 600px` layouts.
- External LLM calls remain opt-in through `.env`; the supplied environment was validated in explicit `LOCAL_DEMO` mode because no embedding model is configured.

## Shared API Contracts

- `GET /api/system/status`: `{ llmMode, embeddingMode, vectorStore, redisEnabled }` wrapped in Result.
- `GET /api/chat/sessions`, `GET /api/chat/sessions/{id}/messages`, `DELETE /api/chat/sessions/{id}`. Message objects include optional `mode` and `citations`.
- SSE keeps the existing `chunk`, `done`, `error` event contract and authorization header; frontend must handle arbitrary network chunk boundaries and aborts.
- `GET /api/documents/{id}`: document metadata including tags. `GET /api/documents/{id}/chunks`: authorized original text chunks. Existing file download endpoint remains.
- `PUT /api/auth/password`: `{ currentPassword, newPassword }`. `PUT /api/auth/profile`: `{ nickname }`.
- `POST /api/admin/users`: `{ username, password, nickname, role }`; `PUT /api/admin/users/{id}/role`: `{ role }`; existing status endpoint retained. Users expose `status`.
- Dashboard preserves existing counts and adds scoped `processingCount`, `userCount`, `chunkCount`, `todayQuestionCount`, `weeklyQuestions`, `weeklyUploads`, `documentTypes`, and `knowledgeBaseRanking`.

## Validation

1. Run `mvn test` with Java 21 and isolated test profile before and after backend changes; regression tests cover async completion, cleanup, authorization, provider streaming and errors.
2. Run `npm run test -- --run` and `npm run build` in `vue`; regressions cover restored citations, SSE fragments, route/session switching and abort behavior.
3. Run `docker compose config --quiet`; start available infrastructure without deleting existing volumes.
4. Exercise login, knowledge creation, upload, polling, reindex, question, refusal, history reload, citations/download, unauthorized access and cleanup through real HTTP.
5. Use browser screenshots at desktop/mobile sizes and inspect console/network errors. Record external AI/embedding service availability independently of local demo results.
