# Knowledge tools implementation plan

**Goal:** Deliver document summary/comparison, a study assistant, private notes, and in-app knowledge-base update notifications.

**Architecture:** Extend the existing Spring Boot REST API and Vue/Element Plus application. Reuse document permissions and parsed chunks; keep generated results and user records in additive SQL tables. Current document parsing and chat remain available. Notifications are in-app only.

**Tech stack:** Java 21, Spring Boot, MyBatis Plus/JdbcTemplate, MySQL/H2, Vue 3, TypeScript, Element Plus, Vitest.

## Document intelligence and study backend
- [x] Create `intelligence` package with controller/service/DTOs. POST `/api/knowledge-bases/{id}/analysis` accepts `{type: 'SUMMARY'|'COMPARE', documentIds: number[], focus?: string}` and returns persisted `{id, knowledgeBaseId, type, title, content, mode, createdAt}`. GET same path lists history.
- [x] POST `/api/knowledge-bases/{id}/study` accepts `{documentIds: number[], questionCount: number}` (3–10). Return study `{id, knowledgeBaseId, title, mode, cards:[{front,back}], questions:[{id,prompt,options:string[]}], createdAt}` without correct answers. GET same path lists sessions; GET `/api/study/{id}` restores a session. POST `/api/study/{id}/submit` accepts `{answers: number[]}` and returns `{score,total,results:[{questionId,prompt,options,selectedIndex,correctIndex,explanation,correct}]}`. Persist attempts and GET `/api/study/mistakes` returns incorrect results with studyId/title, latest resolved state or an explicit delete/resolve route.
- [x] Validate document ownership and readiness, bounded source context, no empty sources, and no invented results on AI failure. Local mode provides clearly labelled extractive cards and source-based exercises. Persist source coverage metadata or clear textual notice for long-document limits.
- [x] Add isolated SQL file `sql/intelligence.sql`; root integrates into `schema.sql`. Test permissions, cardinality, invalid AI responses, grading, history and user isolation.

## Notes
- [x] Create private notes CRUD API `/api/notes`, `{title,content,category,sourceSessionId?}`; owner isolation, validation, search/category filters, updatedAt.
- [x] Create `vue/src/views/Notes.vue`, `vue/src/api/notes.ts`, and reusable `SaveNoteButton.vue`; add save action to ChatMessage. Root connects analysis/study results and navigation. Additive SQL in `sql/notes.sql`.
- [x] Test owner isolation, editing, search and save interaction.

## Knowledge updates
- [x] Add follow/unfollow API for an accessible knowledge base, event storage, unread count/list/read/all-read endpoints. Emit events for upload/reindex, success/failure and deletion; avoid changing existing constructor contracts used in unit tests.
- [x] Add `Notifications.vue`, `NotificationBell.vue`, `FollowKnowledgeBase.vue` with per-user cleanup of polling on unmount and errors. No external email/OS notifications. SQL in `sql/notifications.sql`.
- [x] Test subscription timing, permissions, deduplication, unread transitions and event integration. Root connects navigation and knowledge detail.

## Integration and verification
- [x] Build `DocumentAnalysis.vue` and `StudyAssistant.vue`, API clients and tests for selection validation, asynchronous error handling, source-to-output workflow and quiz grading. Add global study/history access and contextual knowledge-base entry points.
- [x] Merge feature SQL into schema so existing SchemaMigration installs tables without deleting records. Add documentation.
- [x] Run complete backend tests, frontend tests and production build. Independently review permission boundaries, AI grounding, state persistence and frontend races.
- [x] Package backend and verify all four workflows and restored records through the real API; persisted sample result is recorded in `.runtime/feature-smoke-result.json`.
- [x] Restore Docker infrastructure through a normal Docker Desktop restart and start the latest application. Browser checks confirmed saved comparison content, study cards and a restored 3/3 quiz result, private notes, and notification history. No database or Docker data reset was performed.

Work is executed continuously in this task using the subagent-driven-development skill. Existing unrelated working-tree edits are preserved; no commits or publishing are required.
