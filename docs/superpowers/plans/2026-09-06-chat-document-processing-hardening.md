# Chat and Document Processing Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the five confirmed chat/document-processing defects while preserving existing local-demo behavior and API compatibility.

**Architecture:** Authorize chat requests before session/retrieval work, keep newest-first history semantics, and add a cancellable fragment-streaming contract to the existing synchronous AI interface. Move asynchronous document scheduling into a separate Spring bean, let the transactional processor propagate failures, and handle failure cleanup in a new transaction. Update document statuses immediately before each real processing phase.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring MVC `SseEmitter`, MyBatis-Plus, JUnit 5, Mockito, RestClient, Jackson, Maven.

---

## File Map

- Modify `backend/src/main/java/com/example/knowledge/chat/ChatService.java`: enforce knowledge-base access, expose shared synchronous/streaming answer flow, and aggregate partial output.
- Modify `backend/src/main/java/com/example/knowledge/chat/QueryRewriteService.java`: use the newest history item at index `0`.
- Modify `backend/src/main/java/com/example/knowledge/rag/AiService.java`: add the cancellable stream contract with a synchronous fallback.
- Modify `backend/src/main/java/com/example/knowledge/rag/LocalAiService.java`: emit local answer fragments and honor cancellation.
- Modify `backend/src/main/java/com/example/knowledge/rag/OpenAiCompatibleAiService.java`: request provider streaming and parse provider SSE deltas.
- Modify `backend/src/main/java/com/example/knowledge/controller/ChatController.java`: forward fragments, track emitter cancellation, and suppress `done` after cancellation.
- Create `backend/src/main/java/com/example/knowledge/document/DocumentProcessWorker.java`: own the proxied `@Async` entry point and failure handling.
- Modify `backend/src/main/java/com/example/knowledge/document/DocumentProcessService.java`: make the processing transaction propagate failures, split phases, and provide independent failure cleanup.
- Modify `backend/src/main/java/com/example/knowledge/service/DocumentService.java`: inject the worker instead of calling an in-class async method.
- Modify focused tests under `backend/src/test/java/com/example/knowledge/chat/`, `.../rag/`, and `.../document/`.

## Task 1: Protect Chat Retrieval and Fix Follow-Up Context

**Files:**
- Modify: `backend/src/main/java/com/example/knowledge/chat/ChatService.java`
- Modify: `backend/src/main/java/com/example/knowledge/chat/QueryRewriteService.java`
- Modify: `backend/src/test/java/com/example/knowledge/chat/ChatServiceTest.java`
- Modify: `backend/src/test/java/com/example/knowledge/chat/QueryRewriteServiceTest.java`

- [ ] **Step 1: Write the failing authorization regression test**

Add a test that stubs `PermissionService.requireKnowledgeBaseAccess(3L, user, false)` to throw `BusinessException` and verifies no retriever, message mapper, or AI interaction occurs:

```java
@Test
void unauthorizedKnowledgeBaseCannotBeQueried() {
    JwtPrincipal user = new JwtPrincipal(7L, "student", "USER");
    PermissionService permissions = mock(PermissionService.class);
    doThrow(new BusinessException(ErrorCode.FORBIDDEN))
            .when(permissions).requireKnowledgeBaseAccess(3L, user, false);
    HybridRetriever retriever = mock(HybridRetriever.class);
    ChatMessageMapper messages = mock(ChatMessageMapper.class);
    ChatService service = new ChatService(retriever, mock(AiService.class), mock(CitationBuilder.class),
            mock(ChatSessionMapper.class), messages, new QueryRewriteService(), mock(AiUsageLogMapper.class), permissions);

    assertThrows(BusinessException.class, () -> service.answer(3L, "secret", user, null));
    verifyNoInteractions(retriever, messages);
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run from `backend/`:

```powershell
$env:JAVA_HOME='D:\java\java21'
mvn -q -Dtest=ChatServiceTest test
```

Expected: compilation failure because `ChatService` has no constructor accepting `PermissionService`, followed by the missing authorization behavior once the test compiles.

- [ ] **Step 3: Write the failing newest-history test**

Change `QueryRewriteServiceTest` to include two history items and require the newest item:

```java
@Test
void followUpPronounUsesNewestQuestion() {
    QueryRewriteService service = new QueryRewriteService();
    assertEquals("上一轮问题？ 它和 Spring 有什么区别？",
            service.rewrite("它和 Spring 有什么区别？", List.of("上一轮问题？", "最旧问题？")));
}
```

- [ ] **Step 4: Run the rewrite test and confirm RED**

```powershell
mvn -q -Dtest=QueryRewriteServiceTest test
```

Expected: assertion failure showing the implementation currently uses `最旧问题？`.

- [ ] **Step 5: Implement the minimal chat authorization and history fixes**

Add a required `PermissionService` field/constructor parameter. At the first line of `answer`, call:

```java
permissionService.requireKnowledgeBaseAccess(knowledgeBaseId, user, false);
```

Keep the existing descending query and change rewrite selection to:

```java
String previous = history.get(0).trim();
```

Update existing unit-test constructors to pass mocked `AiUsageLogMapper` and `PermissionService` instances, and stub permission success in tests that reach retrieval.

- [ ] **Step 6: Run both tests and commit**

```powershell
mvn -q -Dtest=ChatServiceTest,QueryRewriteServiceTest test
git add backend/src/main/java/com/example/knowledge/chat backend/src/test/java/com/example/knowledge/chat
git commit -m "fix: authorize chat retrieval and use latest history"
```

Expected: both focused test classes pass.

## Task 2: Add a Cancellable AI Fragment Stream

**Files:**
- Modify: `backend/src/main/java/com/example/knowledge/rag/AiService.java`
- Modify: `backend/src/main/java/com/example/knowledge/rag/LocalAiService.java`
- Modify: `backend/src/main/java/com/example/knowledge/rag/OpenAiCompatibleAiService.java`
- Modify: `backend/src/test/java/com/example/knowledge/rag/LocalAiServiceTest.java`

- [ ] **Step 1: Write the failing local stream tests**

Add tests for ordered fragments and cancellation:

```java
@Test
void streamEmitsAnswerFragmentsInOrder() {
    LocalAiService service = new LocalAiService(new PromptBuilder());
    List<String> fragments = new ArrayList<>();
    DocumentChunk chunk = new DocumentChunk();
    chunk.setContent("第一段");
    RetrievedChunk retrieved = new RetrievedChunk(chunk, 1.0, 1.0, 1.0);
    AiResponse result = service.stream(new AiRequest("问题", List.of(retrieved), true), fragments::add, () -> false);
    assertEquals("LOCAL_DEMO", result.mode());
    assertEquals(result.answer(), String.join("", fragments));
    assertTrue(fragments.size() > 1);
}

@Test
void streamStopsWhenCancelled() {
    LocalAiService service = new LocalAiService(new PromptBuilder());
    List<String> fragments = new ArrayList<>();
    DocumentChunk chunk = new DocumentChunk();
    chunk.setContent("一段足够长的内容");
    RetrievedChunk retrieved = new RetrievedChunk(chunk, 1.0, 1.0, 1.0);
    service.stream(new AiRequest("问题", List.of(retrieved), true), fragments::add,
            () -> fragments.size() >= 1);
    assertEquals(1, fragments.size());
}
```

- [ ] **Step 2: Run the focused AI test and confirm RED**

```powershell
mvn -q -Dtest=LocalAiServiceTest test
```

Expected: compilation failure because `AiService.stream` does not exist.

- [ ] **Step 3: Implement the stream contract and local adapter**

In `AiService`, add:

```java
default AiResponse stream(AiRequest request, Consumer<String> onChunk, BooleanSupplier cancelled) {
    AiResponse response = generate(request);
    if (response.answer() != null && !cancelled.getAsBoolean()) onChunk.accept(response.answer());
    return response;
}
```

In `LocalAiService`, build the same answer as `generate`, split it into bounded fragments of exactly 24 Unicode code points (the final fragment may be shorter), call `onChunk` for each fragment, and check `cancelled` before every callback. Keep `generate` unchanged so existing tests remain valid.

- [ ] **Step 4: Implement provider SSE parsing**

In `OpenAiCompatibleAiService`, post the existing request body with `stream=true`, consume `ClientHttpResponse.getBody()` line by line, ignore non-`data:` lines, stop on `[DONE]`, parse each JSON data record with Jackson, extract `choices[0].delta.content`, and forward non-empty deltas until `cancelled` is true. Accumulate deltas into the returned `AiResponse` with mode `OPENAI_COMPATIBLE`. Preserve the existing synchronous path for `generate`.

- [ ] **Step 5: Run AI tests and commit**

```powershell
mvn -q -Dtest=LocalAiServiceTest test
git add backend/src/main/java/com/example/knowledge/rag backend/src/test/java/com/example/knowledge/rag/LocalAiServiceTest.java
git commit -m "feat: add cancellable AI answer streaming"
```

Expected: local synchronous and streaming tests pass.

## Task 3: Wire Streaming Through ChatService and ChatController

**Files:**
- Modify: `backend/src/main/java/com/example/knowledge/chat/ChatService.java`
- Modify: `backend/src/main/java/com/example/knowledge/controller/ChatController.java`
- Modify: `backend/src/test/java/com/example/knowledge/chat/ChatServiceTest.java`
- Modify: `backend/src/test/java/com/example/knowledge/chat/ChatControllerSseTest.java`

- [ ] **Step 1: Write failing service/controller stream tests**

Add a service test where a mocked `AiService.stream` invokes `onChunk.accept("第一段")` and then `onChunk.accept("第二段")`, returns `new AiResponse("第一段第二段", "TEST")`, and assert the returned answer is `第一段第二段` and the assistant message is saved once. Add a controller test with a stub service that invokes the callback twice and assert the emitter receives two `message` events followed by `citation` and `done`.

- [ ] **Step 2: Run focused tests and confirm RED**

```powershell
mvn -q -Dtest=ChatServiceTest,ChatControllerSseTest test
```

Expected: compilation failure for the new stream entry point and assertion failure for one-shot SSE behavior.

- [ ] **Step 3: Implement shared chat answer flow**

Add a method with this contract:

```java
public ChatAnswer answerStream(Long knowledgeBaseId, String question, JwtPrincipal user,
                               Long sessionId, Consumer<String> onChunk, BooleanSupplier cancelled)
```

Move authorization, session/history/rewrite, user-message persistence, retrieval, citation construction, AI usage logging, and assistant persistence into this method. For sufficient retrieval call `aiService.stream`; append every callback fragment to a `StringBuilder` and forward it to `onChunk`. After the adapter returns, if no callback was emitted, append its returned `AiResponse.answer()` once so the synchronous `answer(...)` delegation still returns the full answer. For insufficient retrieval emit the refusal through the callback and append the same refusal to the aggregate; skip AI. Make `answer(...)` delegate with `chunk -> {}` and `() -> false`.

- [ ] **Step 4: Implement cancellable SSE emission**

In `ChatController`, create an `AtomicBoolean cancelled`. Register `onCompletion`, `onTimeout`, and `onError` handlers that set it. Call `service.answerStream` from the existing `CompletableFuture`, send each callback fragment as `SseEmitter.event().name("message").data(fragment)`, then send citations and `done` only when the cancellation flag is false. On `IOException`, set cancellation and let the existing error path complete the emitter. Never expose the exception message in the `error` event.

- [ ] **Step 5: Run chat tests and commit**

```powershell
mvn -q -Dtest=ChatServiceTest,ChatControllerSseTest test
git add backend/src/main/java/com/example/knowledge/chat/ChatService.java backend/src/main/java/com/example/knowledge/controller/ChatController.java backend/src/test/java/com/example/knowledge/chat
git commit -m "feat: stream chat answers with cancellation"
```

Expected: fragment ordering, citations, normal completion, and cancellation tests pass.

## Task 4: Move Async Scheduling Behind a Transactional Worker

**Files:**
- Create: `backend/src/main/java/com/example/knowledge/document/DocumentProcessWorker.java`
- Modify: `backend/src/main/java/com/example/knowledge/document/DocumentProcessService.java`
- Modify: `backend/src/main/java/com/example/knowledge/service/DocumentService.java`
- Create: `backend/src/test/java/com/example/knowledge/document/DocumentProcessServiceTest.java`

- [ ] **Step 1: Write the failing processor tests**

Add tests that use mocked mappers/parsers/embedding/vector dependencies to verify status order (`PARSING`, `CHUNKING`, `EMBEDDING`, `INDEXING`, `SUCCESS`) and that an embedding failure escapes `process()` instead of being swallowed. Add a worker test that supplies a failing processor and verifies `deleteByDocument`, chunk/tag deletion, and a final `FAILED` update.

- [ ] **Step 2: Run the document test and confirm RED**

```powershell
mvn -q -Dtest=DocumentProcessServiceTest test
```

Expected: compilation failure because `DocumentProcessWorker` and the new cleanup contract do not exist, plus the current processor test would observe the wrong status order.

- [ ] **Step 3: Extract the proxied async worker**

Create:

```java
@Service
public class DocumentProcessWorker {
    private final DocumentProcessService processor;

    public DocumentProcessWorker(DocumentProcessService processor) { this.processor = processor; }

    @Async
    public void processAsync(Long documentId) {
        try {
            processor.process(documentId);
        } catch (Exception exception) {
            processor.markFailed(documentId, exception);
        }
    }
}
```

Change `DocumentService` to inject `DocumentProcessWorker` and call `worker.processAsync(...)`. Remove `@Async processAsync` from `DocumentProcessService`.

- [ ] **Step 4: Make processing transactional and failure-safe**

Keep `@Transactional` on `process`, remove its broad `catch`, and let exceptions propagate. Insert all chunks first, set `EMBEDDING`, call `embedBatch`, set `INDEXING`, then upsert each vector. Add:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void markFailed(Long documentId, Exception exception) {
    chunkMapper.delete(new QueryWrapper<DocumentChunk>().eq("document_id", documentId));
    tagMapper.delete(new QueryWrapper<DocumentTag>().eq("document_id", documentId));
    vectorStore.deleteByDocument(documentId);
    Document document = documentMapper.selectById(documentId);
    if (document != null) {
        document.setStatus("FAILED");
        document.setErrorMessage(errorMessage(exception));
        documentMapper.updateById(document);
    }
}
```

Use `embedBatch(chunks.stream().map(DocumentChunk::getContent).toList())`, reject a vector-count mismatch with an `IllegalStateException`, and keep the existing metadata/tag success path after indexing.

- [ ] **Step 5: Run document tests and commit**

```powershell
mvn -q -Dtest=DocumentProcessServiceTest test
git add backend/src/main/java/com/example/knowledge/document backend/src/main/java/com/example/knowledge/service/DocumentService.java backend/src/test/java/com/example/knowledge/document/DocumentProcessServiceTest.java
git commit -m "fix: make document processing transactional"
```

Expected: stage order, propagated failure, and worker cleanup tests pass.

## Task 5: Full Regression Verification and Diff Review

**Files:**
- Modify only files listed in Tasks 1–4.

- [ ] **Step 1: Run the complete backend suite**

```powershell
$env:JAVA_HOME='D:\java\java21'
Set-Location backend
mvn -q test
```

Expected: Maven exits with code `0` and all tests pass.

- [ ] **Step 2: Run a source-level regression scan**

```powershell
rg -n "history\.get\(history\.size\(\) - 1\)|processAsync\(Long documentId\).*process\(|emitter\.send\(SseEmitter\.event\(\)\.name\(\"message\"\).*answer\.answer\(\)" backend/src/main/java
git diff --check
git status --short
```

Expected: the old history expression, in-class async self-call, and one-shot answer send are absent; diff check is clean; the pre-existing untracked user document remains untouched.

- [ ] **Step 3: Inspect the final diff and commit verification metadata**

```powershell
git diff --stat HEAD~4..HEAD
git log -5 --oneline
```

Confirm the diff contains only the hardening implementation/tests plus the already committed design and plan documents. Do not stage or delete the unrelated user document.
