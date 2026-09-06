# Chat and Document Processing Hardening Design

## 1. Goal and Scope

This change addresses five confirmed defects in the existing V1 implementation:

1. Chat retrieval must enforce knowledge-base read access on the backend.
2. Follow-up query rewriting must use the most recent user question.
3. Chat SSE must deliver answer fragments and support cancellation rather than sending one completed answer only.
4. Document processing must execute through an actual asynchronous Spring proxy and must not commit partial chunks after a failure.
5. Document status transitions must reflect the real parsing, chunking, embedding, and indexing phases.

The change is limited to the backend chat/document processing flow and its focused tests. It does not add Redis, MinIO, a standalone reranker, password change APIs, or the unrelated admin/dashboard frontend integrations.

## 2. Architecture

### 2.1 Chat authorization and history

`ChatService` receives a required `PermissionService` dependency. `answer` and the streaming entry point call `requireKnowledgeBaseAccess(knowledgeBaseId, user, false)` before session creation, message loading, or retrieval. Unauthorized users therefore cannot observe retrieval or AI side effects.

History remains loaded with `created_at DESC LIMIT 10`. The `QueryRewriteService` contract treats index `0` as the latest user question and uses `history.get(0)` for pronoun follow-ups.

### 2.2 AI streaming contract

`AiService.generate(AiRequest)` remains available for synchronous callers. A new `stream(AiRequest, Consumer<String>, BooleanSupplier)` contract emits answer fragments and returns the final `AiResponse` metadata. Its default implementation calls `generate` and emits deterministic fragments so existing adapters remain compatible.

`LocalAiService` uses the same contract and checks the cancellation predicate between fragments. `OpenAiCompatibleAiService` requests `stream=true`, reads provider SSE `data:` records, forwards text deltas, and stops when cancellation is requested. Provider errors propagate to the chat service.

`ChatService` adds a streaming answer method that performs authorization, history/rewrite, persistence, retrieval, and citation construction. It aggregates every emitted fragment. A normal completion saves the complete assistant answer; cancellation or a client disconnect saves the generated prefix. The existing synchronous `answer` delegates to the same business path with a no-op callback.

`ChatController` runs the stream on the existing executor, sends each callback fragment as a `message` SSE event, then sends `citation` and `done` only after normal completion. Emitter completion, timeout, error, and send failures set a cancellation flag. Cancelled streams do not emit `done`.

### 2.3 Document processing and transactions

`DocumentProcessWorker` owns the `@Async` method and depends on `DocumentProcessService`. This ensures the call to `process` crosses a Spring proxy. `DocumentProcessService.process` is transactional and does not swallow processing exceptions.

The processing stages are:

1. Set `PARSING`, parse the file.
2. Set `CHUNKING`, build and insert document chunks.
3. Set `EMBEDDING`, generate embeddings for all chunks with `embedBatch`.
4. Set `INDEXING`, upsert all chunk/vector pairs.
5. Set document metadata and `SUCCESS`.

If any stage fails, the transaction rolls back by propagating the exception. The worker then invokes an independent transaction that deletes the document's chunks, tags, and vectors and sets `FAILED` with a useful error message. Retry begins from `WAITING` and uses the same cleanup path.

## 3. Error Handling and Compatibility

- Knowledge-base authorization failures use the existing `BusinessException`/`ErrorCode.FORBIDDEN` path.
- Insufficient retrieval context still produces the existing local refusal and never calls the AI adapter.
- AI stream failures send one `error` SSE event and complete the emitter with an error; raw provider details are not exposed to the client.
- Client cancellation is treated as a partial-answer completion for persistence, not as a successful `done` event.
- Existing synchronous AI tests and callers continue to use `generate`.
- Existing constructor-based unit tests are updated to provide the new required permission/worker dependencies.

## 4. Verification

Focused backend tests cover:

- unauthorized chat access short-circuiting before retriever/AI calls;
- latest-question selection in follow-up rewriting;
- ordered SSE message fragments, citations, and normal completion;
- cancellation stopping further fragments and suppressing `done`;
- document stage ordering;
- transaction failure propagation and worker cleanup/status update;
- local AI stream fragmenting and cancellation.

The full verification command is:

```powershell
$env:JAVA_HOME='D:\java\java21'
Set-Location backend
mvn -q test
```

The final diff must contain only this hardening change and its tests, while preserving unrelated user files.
