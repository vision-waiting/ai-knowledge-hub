# 系统架构

```text
Vue 3 + TypeScript
        │ REST / SSE
        ▼
Spring Boot 3 ── MySQL（业务数据）
        ├─────── 本地 uploads（原始文件）
        ├─────── Redis（可选状态/限流）
        ├─────── Qdrant（可选向量索引）
        └─────── AI Service（本地演示或 OpenAI 兼容 API）
```

上传接口只创建文档记录并返回 202，`DocumentProcessService` 使用 `@Async` 完成解析、切块、Embedding 和索引状态更新。RAG 模块通过 `DocumentParser`、`EmbeddingService`、`VectorStore` 和 `AiService` 接口隔离外部依赖。

