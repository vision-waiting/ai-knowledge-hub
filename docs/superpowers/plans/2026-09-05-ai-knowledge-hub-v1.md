# AI Knowledge Hub V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable graduation-project MVP of AI Knowledge Hub with Vue 3, Spring Boot, MySQL-compatible persistence, local document processing, RAG-style retrieval, citations, SSE chat, permissions, and optional Redis/Qdrant/OpenAI-compatible integrations.

**Architecture:** The Spring Boot backend owns authentication, authorization, persistence, file processing, retrieval, chat, and administration. The Vue frontend consumes a uniform REST/SSE API. Interfaces isolate parsers, embedding, AI generation, vector storage, and retrieval so the default local demo mode works without external AI while production integrations can be enabled with environment variables.

**Tech Stack:** Java 21, Spring Boot 3, Maven, Spring Security, JWT, MyBatis-Plus, MySQL, H2 test profile, Redis optional, Qdrant optional, Vue 3, TypeScript, Vite, Element Plus, Pinia, Vue Router, Axios, ECharts, markdown-it, Vitest.

---

## File Map

### Backend (`SpringBoot/`)

- `pom.xml`: Spring Boot, Web, Validation, Security, JWT, MyBatis-Plus, MySQL, H2, Apache Tika/PDFBox, test dependencies.
- `src/main/java/com/example/knowledge/KnowledgeApplication.java`: application entry point and async configuration.
- `src/main/java/com/example/knowledge/common/`: `Result`, `PageResult`, error codes, business exception, global handler.
- `src/main/java/com/example/knowledge/config/`: security, MyBatis, properties, async executor, CORS.
- `src/main/java/com/example/knowledge/security/`: JWT provider/filter, current-user helper, password encoder.
- `src/main/java/com/example/knowledge/entity/`: user, knowledge base, document, chunk, tag, chat session/message, operation log, AI usage entities.
- `src/main/java/com/example/knowledge/mapper/`: MyBatis-Plus mapper interfaces.
- `src/main/java/com/example/knowledge/dto/` and `vo/`: request/response contracts.
- `src/main/java/com/example/knowledge/controller/`: auth, dashboard, knowledge base, document, chat, admin controllers.
- `src/main/java/com/example/knowledge/service/`: business services and implementations.
- `src/main/java/com/example/knowledge/document/`: parser interface, PDF/TXT/Markdown parsers, cleaner, chunker, async processor.
- `src/main/java/com/example/knowledge/rag/`: embedding, vector store, keyword retriever, hybrid retriever, prompt builder, citation builder, AI adapters.
- `src/main/resources/application.yml`, `application-dev.yml`, `application-test.yml`: environment-safe configuration.
- `src/main/resources/schema.sql`, `data.sql`: database schema and demo admin/user seed.
- `src/test/java/...`: unit and API tests.

### Frontend (`vue/`)

- `package.json`, `vite.config.ts`, `tsconfig.json`: build and test setup.
- `src/main.ts`, `src/App.vue`, `src/router/index.ts`: app shell and route guards.
- `src/api/http.ts`, `auth.ts`, `knowledge.ts`, `documents.ts`, `chat.ts`, `dashboard.ts`, `admin.ts`: typed API clients.
- `src/stores/auth.ts`, `chat.ts`: Pinia state.
- `src/layouts/AppLayout.vue`: sidebar navigation, user menu, responsive shell.
- `src/components/KnowledgeCard.vue`, `FileUpload.vue`, `DocumentStatus.vue`, `ChatMessage.vue`, `CitationCard.vue`, `StatCard.vue`.
- `src/views/Login.vue`, `Dashboard.vue`, `KnowledgeList.vue`, `KnowledgeDetail.vue`, `DocumentList.vue`, `Chat.vue`, `ChatHistory.vue`, `Profile.vue`, `AdminUsers.vue`, `AdminLogs.vue`, `AdminStatistics.vue`.
- `src/types/index.ts`, `src/utils/format.ts`, `src/utils/markdown.ts`.
- `src/**/*.spec.ts`: focused component/store tests.

### Root

- `docker-compose.yml`: MySQL, Redis, Qdrant, optional MinIO services with health checks.
- `.env.example`: non-secret configuration names only.
- `.gitignore`: Java/Vite/build/upload/runtime files and `.superpowers/`.
- `README.md`: setup, local demo mode, Docker services, API examples, test commands, and demo script.
- `sql/init.sql`: deployment-friendly schema matching backend entities.

---

### Task 1: Scaffold the workspace and local runtime

**Files:**
- Create: `SpringBoot/pom.xml`, `SpringBoot/src/main/java/com/example/knowledge/KnowledgeApplication.java`
- Create: `vue/package.json`, `vue/vite.config.ts`, `vue/src/main.ts`
- Create: `docker-compose.yml`, `.env.example`, `.gitignore`, `README.md`
- Test: run Maven and npm dependency/build checks

- [ ] **Step 1: Add root runtime files**

Create `.env.example` with `SPRING_PROFILES_ACTIVE=dev`, `MYSQL_*`, `REDIS_*`, `QDRANT_*`, `LLM_*`, `EMBEDDING_*`, and `UPLOAD_DIR=./uploads`. Add `.gitignore` entries for `.env`, `uploads/`, `target/`, `node_modules/`, `dist/`, `.superpowers/`, IDE files, and logs.

- [ ] **Step 2: Add Docker Compose services**

Define MySQL 8 on `3306`, Redis 7 on `6379`, Qdrant on `6333`, and MinIO on `9000/9001`, each with named volumes and health checks. Do not put credentials other than development defaults in source-controlled files.

- [ ] **Step 3: Add minimal backend and frontend bootstraps**

The backend main class must be:

```java
@SpringBootApplication
@EnableAsync
public class KnowledgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}
```

The frontend `main.ts` must create and mount a Vue app with router and Pinia providers.

- [ ] **Step 4: Verify the empty scaffold**

Run `mvn -q -DskipTests package` in `SpringBoot/` with `JAVA_HOME=D:\java\java21`, then run `npm install` and `npm run build` in `vue/`. Expected: both commands exit 0.

- [ ] **Step 5: Commit**

```bash
git add backend frontend docker-compose.yml .env.example .gitignore README.md
git commit -m "chore: scaffold full stack workspace"
```

### Task 2: Implement common backend contracts and database schema

**Files:**
- Create: `SpringBoot/src/main/java/com/example/knowledge/common/Result.java`, `PageResult.java`, `ErrorCode.java`, `BusinessException.java`, `GlobalExceptionHandler.java`
- Create: `SpringBoot/src/main/resources/application.yml`, `application-dev.yml`, `application-test.yml`
- Create: `sql/schema.sql`, `data.sql`, `sql/init.sql`
- Test: `SpringBoot/src/test/java/com/example/knowledge/common/ResultTest.java`

- [ ] **Step 1: Write the failing common-contract test**

```java
@Test
void successResultContainsCodeMessageDataAndTimestamp() {
    Result<String> result = Result.success("ok");
    assertEquals(200, result.code());
    assertEquals("success", result.message());
    assertEquals("ok", result.data());
    assertTrue(result.timestamp() > 0);
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run `mvn -q -Dtest=ResultTest test` in `SpringBoot/`. Expected: compilation failure because `Result` does not exist.

- [ ] **Step 3: Implement the contracts**

Use a generic record for the response and an exception carrying an `ErrorCode`. The global handler maps validation errors to code `400`, authentication to `401`, authorization to `403`, missing resources to `404`, and unknown exceptions to `500`.

- [ ] **Step 4: Add schema and profiles**

Create the nine tables in the design spec with `BIGINT` IDs, indexed ownership/status columns, UTF-8 `LONGTEXT` content, and `created_at`/`updated_at`. Test profile uses H2 MySQL mode; dev profile points to MySQL but allows local fallback settings.

- [ ] **Step 5: Run the test and commit**

Run `mvn -q -Dtest=ResultTest test`; expected: PASS. Commit with `git commit -m "feat: add backend contracts and schema"`.

### Task 3: Add authentication, JWT, roles, and resource ownership

**Files:**
- Create: `entity/SysUser.java`, `mapper/SysUserMapper.java`, `service/UserService.java`, `service/impl/UserServiceImpl.java`
- Create: `security/JwtTokenProvider.java`, `JwtAuthenticationFilter.java`, `CurrentUser.java`, `config/SecurityConfig.java`
- Create: `dto/LoginRequest.java`, `vo/LoginVO.java`, `controller/AuthController.java`
- Test: `security/AuthServiceTest.java`, `controller/AuthControllerTest.java`

- [ ] **Step 1: Write failing authentication tests**

Cover BCrypt password verification, invalid credentials returning `401`, JWT extraction, and `ADMIN`/`USER` role mapping. Use H2 and real Spring components; mock only the token clock and password hash where necessary.

- [ ] **Step 2: Run tests to verify the missing implementation fails**

Run `mvn -q -Dtest=AuthServiceTest,AuthControllerTest test`. Expected: missing classes or failing assertions.

- [ ] **Step 3: Implement the minimal auth flow**

Add BCrypt encoding, a JWT secret from `JWT_SECRET`, 24-hour expiry, `/api/auth/login`, `/api/auth/me`, and `/api/auth/logout`. Logout should invalidate the token identifier in an in-memory/Redis blacklist adapter.

- [ ] **Step 4: Add security rules**

Permit `/api/auth/login`, `/actuator/health`, and static docs. Require authentication for all other `/api/**`; require `ROLE_ADMIN` for `/api/admin/**`. Add CORS for the frontend origin.

- [ ] **Step 5: Run tests and commit**

Run `mvn -q -Dtest=AuthServiceTest,AuthControllerTest test`; expected: PASS. Commit `feat: add jwt authentication and role security`.

### Task 4: Implement knowledge-base CRUD and Dashboard API

**Files:**
- Create: `entity/KnowledgeBase.java`, mapper, DTOs, VOs, `KnowledgeBaseService`, `KnowledgeBaseController`
- Create: `DashboardService`, `DashboardController`, statistics VOs
- Modify: `service/PermissionService.java` and tests
- Test: `KnowledgeBaseServiceTest.java`, `KnowledgeBaseControllerTest.java`

- [ ] **Step 1: Write failing ownership and CRUD tests**

Test that a USER can create/read/update/delete owned bases, cannot read another user’s base, and ADMIN can read all bases. Test list search by name and overview counts.

- [ ] **Step 2: Run tests and verify RED**

Run `mvn -q -Dtest=KnowledgeBaseServiceTest,KnowledgeBaseControllerTest test`; expected: missing service/controller behavior.

- [ ] **Step 3: Implement service and controllers**

Use `PermissionService.requireKnowledgeBaseAccess(userId, baseId, write)` before every mutation/read. Keep `document_count` maintained by document service, not by controllers.

- [ ] **Step 4: Implement dashboard aggregations**

Return knowledge base count, document count, successful/failed processing counts, chat count, AI calls, last-seven-day series, and file-type distribution. Use SQL aggregation in mappers and return empty series when no records exist.

- [ ] **Step 5: Run tests and commit**

Run the focused test command and `mvn -q test`; expected: PASS. Commit `feat: add knowledge base and dashboard APIs`.

### Task 5: Implement document upload, parsing, chunking, and async states

**Files:**
- Create: `entity/Document.java`, `DocumentChunk.java`, `DocumentTag.java`, mappers, DTOs, VOs
- Create: `document/DocumentParser.java`, `ParsedDocument.java`, `PdfParser.java`, `TextParser.java`, `MarkdownParser.java`, `TextChunker.java`, `DocumentProcessService.java`
- Create: `DocumentController.java`, `DocumentService.java`, `StorageService.java`
- Test: parser, chunker, upload validation, and processing state tests

- [ ] **Step 1: Write failing parser and chunker tests**

```java
@Test
void markdownParserKeepsHeadingAndParagraphText() { /* assert title and body */ }

@Test
void chunkerCreatesOverlappingChunksWithoutEmptyItems() { /* assert count, overlap, non-empty */ }
```

Add tests for rejected `.exe` and files larger than 50 MB.

- [ ] **Step 2: Run tests to verify RED**

Run `mvn -q -Dtest=MarkdownParserTest,TextChunkerTest,UploadValidationTest test`; expected: missing implementations or failed assertions.

- [ ] **Step 3: Implement parsers and storage**

Use PDFBox for PDF text/page extraction, UTF-8 readers for TXT/Markdown, and Apache Tika for MIME detection. Store files under `UPLOAD_DIR/{knowledgeBaseId}/{uuid}.{extension}`. Never use the original name as a path.

- [ ] **Step 4: Implement async processing**

On upload, insert `WAITING` and return `202`. An `@Async` processor transitions through `PARSING`, `CHUNKING`, `EMBEDDING`, `INDEXING`, and `SUCCESS`; catch exceptions, set `FAILED` and `error_message`, and never block the upload request.

- [ ] **Step 5: Run tests and commit**

Run parser/chunker tests and `mvn -q test`; expected: PASS. Commit `feat: add asynchronous document processing`.

### Task 6: Implement local RAG adapters, citations, and optional integrations

**Files:**
- Create: `rag/EmbeddingService.java`, `LocalEmbeddingService.java`, `OpenAiEmbeddingService.java`
- Create: `rag/VectorStore.java`, `LocalVectorStore.java`, `QdrantVectorStore.java`
- Create: `rag/KeywordRetriever.java`, `HybridRetriever.java`, `PromptBuilder.java`, `CitationBuilder.java`
- Create: `rag/AiService.java`, `LocalAiService.java`, `OpenAiCompatibleAiService.java`, `AiModeProperties.java`
- Test: `HybridRetrieverTest.java`, `CitationBuilderTest.java`, `LocalAiServiceTest.java`

- [ ] **Step 1: Write failing retrieval tests**

Test `0.7 * vector + 0.3 * keyword` ordering, knowledge-base filtering, threshold rejection, and stable Citation numbering containing document ID, filename, page, chunk ID, and content excerpt.

- [ ] **Step 2: Run tests and verify RED**

Run `mvn -q -Dtest=HybridRetrieverTest,CitationBuilderTest,LocalAiServiceTest test`; expected: missing classes or failed ordering assertions.

- [ ] **Step 3: Implement deterministic local adapters**

Generate a normalized fixed-size vector from token hashes for local mode. Score keyword overlap using lowercase token frequency. Local AI composes a Chinese answer from top chunks and explicitly reports missing context below the threshold; it must label `mode=LOCAL_DEMO` in the usage log.

- [ ] **Step 4: Implement optional Qdrant and OpenAI-compatible adapters**

Read URLs, model names, and keys from properties. Use HTTP clients with timeouts, never log keys, and throw a typed `AiUnavailableException` for network/auth failures so the caller can show a clear fallback message.

- [ ] **Step 5: Run tests and commit**

Run focused tests and `mvn -q test`; expected: PASS. Commit `feat: add pluggable rag and ai adapters`.

### Task 7: Implement chat sessions, multi-turn rewrite, SSE, and history

**Files:**
- Create: chat entities/mappers/DTOs/VOs, `ChatService`, `QueryRewriteService`, `ChatController`
- Modify: `AiService`, `HybridRetriever`, `AiUsageLogService`
- Test: `ChatServiceTest.java`, `ChatControllerSseTest.java`, `QueryRewriteServiceTest.java`

- [ ] **Step 1: Write failing multi-turn and threshold tests**

Test that a follow-up “它和 Spring 有什么区别？” is rewritten with the prior topic, that no-context questions do not call `AiService.generate`, and that messages are saved with `USER`/`ASSISTANT` roles.

- [ ] **Step 2: Verify RED**

Run the focused test command and confirm failures are due to missing rewrite/chat behavior.

- [ ] **Step 3: Implement sessions and persistence**

Create or reuse a session per knowledge base, load the latest 10 messages for context, store user input before retrieval and assistant output after completion, and update session title from the first question.

- [ ] **Step 4: Implement SSE**

Return `text/event-stream` with JSON events: `message` for answer fragments, `citation` for sources, `done` with message/session IDs, and `error` on failure. Use an executor to avoid blocking request threads and enforce one active generation per session.

- [ ] **Step 5: Run tests and commit**

Run `mvn -q test`; expected: PASS. Commit `feat: add multi-turn streaming chat`.

### Task 8: Build frontend shell, auth, knowledge, and document workflows

**Files:**
- Create all files listed under `vue/src/api`, `stores`, `layouts`, `components`, and views for Login, Dashboard, KnowledgeList, KnowledgeDetail, DocumentList.
- Test: `src/stores/auth.spec.ts`, `src/components/FileUpload.spec.ts`, `src/router/index.spec.ts`

- [ ] **Step 1: Write failing frontend tests**

Test token persistence, route redirect from protected page to `/login`, upload progress/status rendering, and the knowledge card emitting an open event.

- [ ] **Step 2: Run tests to verify RED**

Run `npm run test -- --run src/stores/auth.spec.ts src/components/FileUpload.spec.ts src/router/index.spec.ts`; expected: missing modules or failed assertions.

- [ ] **Step 3: Implement typed HTTP clients and auth store**

Create an Axios instance that adds `Authorization: Bearer <token>`, unwraps `Result.data`, redirects on `401`, and exposes `login`, `logout`, and `loadMe` actions in Pinia.

- [ ] **Step 4: Implement layout and CRUD views**

Build the dark sidebar/workbench layout, knowledge cards, search/create/edit/delete dialogs, document table, upload drawer, status timeline, summary/tags panel, download action, and reindex action. Keep all visible controls keyboard accessible.

- [ ] **Step 5: Run tests and commit**

Run focused Vitest tests and `npm run build`; expected: PASS. Commit `feat: add frontend workbench and document flows`.

### Task 9: Build chat UI, citations, dashboard charts, and admin pages

**Files:**
- Create/modify `vue/src/views/Chat.vue`, `ChatHistory.vue`, `Dashboard.vue`, `AdminUsers.vue`, `AdminLogs.vue`, `AdminStatistics.vue`
- Create/modify `vue/src/components/ChatMessage.vue`, `CitationCard.vue`, `StatCard.vue`, `src/utils/markdown.ts`, `src/stores/chat.ts`
- Test: `Chat.spec.ts`, `markdown.spec.ts`, `Dashboard.spec.ts`

- [ ] **Step 1: Write failing UI tests**

Test Markdown code rendering, SSE fragments concatenating into one assistant message, citation click emitting document/page data, and dashboard empty-state chart handling.

- [ ] **Step 2: Verify RED**

Run `npm run test -- --run src/views/Chat.spec.ts src/utils/markdown.spec.ts src/views/Dashboard.spec.ts`; expected: failed assertions for missing behavior.

- [ ] **Step 3: Implement chat store and SSE client**

Use `fetch` with an AbortController for `/api/chat/stream`, parse `event:`/`data:` records, append fragments reactively, stop on `done`, and show retry state on `error`.

- [ ] **Step 4: Implement chat and citation views**

Render user/assistant bubbles, Markdown, source chips, source detail drawer, clear/stop/retry actions, session list, and an explicit low-context notice. Never expose raw server stack traces.

- [ ] **Step 5: Implement Dashboard/Admin**

Use ECharts for the seven-day trend and file-type distribution, tables for users/logs/AI calls, and role-gated routes. Show the active AI mode and dependency status in an unobtrusive status panel.

- [ ] **Step 6: Run tests and commit**

Run all Vitest tests and `npm run build`; expected: PASS. Commit `feat: add chat citations and admin views`.

### Task 10: Wire deployment docs, integration tests, and demo data

**Files:**
- Modify: `README.md`, `docker-compose.yml`, `.env.example`
- Create: `SpringBoot/src/test/java/.../ApiIntegrationTest.java`, `vue/tests/demo-flow.spec.ts`, `knowledge-data/java/README.md`
- Create: `docs/architecture.md`, `docs/database.md`, `docs/api.md`, `docs/rag.md`, `docs/deployment.md`

- [ ] **Step 1: Add backend integration tests**

Use Spring Boot test profile with H2 and MockMvc to execute login → create knowledge base → upload TXT → poll status → list documents → start chat. Assert HTTP status, ownership, citation fields, and delete cleanup.

- [ ] **Step 2: Add frontend demo-flow test**

Use a local mocked API server only for browser orchestration; assert the real UI flow and visible Chinese states, including failed-document retry and no-context answer.

- [ ] **Step 3: Add demo seed and documentation**

Seed admin `admin/123456` and user `student/123456` only in the dev/test profile, document that credentials are for local demo only, and add sample TXT/Markdown files under `knowledge-data/java/`.

- [ ] **Step 4: Run the complete verification suite**

Run:

```powershell
$env:JAVA_HOME='D:\java\java21'; cd backend; mvn -q test
cd ..\frontend; npm run test -- --run; npm run build
cd ..; docker compose config
```

Expected: all tests pass, frontend build succeeds, and Compose configuration is valid. Start the SpringBoot/frontend and manually execute the documented 5–10 minute demo path.

- [ ] **Step 5: Commit and inspect the final diff**

```bash
git add .
git commit -m "feat: complete AI Knowledge Hub V1"
git status --short --branch
git diff HEAD~1 --stat
```

Expected: only project files are staged, `.env` and runtime uploads are ignored, and the final diff includes no API keys.

## Plan Self-Review

- Spec coverage: authentication, roles, knowledge bases, documents, parsers, chunking, embedding, Qdrant adapter, hybrid retrieval, threshold refusal, citations, multi-turn chat, SSE, summaries/tags, logs, statistics, tests, Docker, and demo flow each have an explicit task.
- Placeholder scan: no unfinished markers or unspecified validation steps are used.
- Type consistency: `Result<T>`, `DocumentParser`, `EmbeddingService`, `AiService`, `VectorStore`, `HybridRetriever`, `CitationBuilder`, route names, and status values are used consistently across tasks.
- Scope: the plan produces a runnable local V1 first and keeps external services behind adapters; enhancement integrations do not block the local demo.
