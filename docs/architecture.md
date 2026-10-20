# 系统架构

```text
Vue 3 + TypeScript
        │ REST / SSE
        ▼
Spring Boot 3 ── MySQL（业务数据）
        ├─────── 本地 uploads（原始文件）
        ├─────── Redis（可选限流）
        ├─────── Qdrant（可选向量索引）
        └─────── AI Service（原文整理或兼容 API 的本地/远程模型）
```

上传接口保存原文件、创建文档记录并返回 HTTP 202。事务提交后，`DocumentProcessWorker` 通过 `@Async` 调用 `DocumentProcessService`，完成解析、切块、Embedding 和索引状态更新；前端轮询处理状态，只有 `SUCCESS` 文档参与检索。

RAG 模块通过 `DocumentParser`、`EmbeddingService`、`VectorStore` 和 `AiService` 接口隔离外部依赖。语言模型、向量生成与向量存储分别配置，支持本地模型或远程兼容服务；未启用语言模型时提供明确标注的原文整理。`LocalEmbeddingService` 使用确定性 hash 向量，不等同于语义模型。未启用 Qdrant 时，`LocalVectorStore` 从数据库读取片段并在查询时计算向量相似度，不持久保存独立向量索引。

## 权限与数据边界

`PermissionService` 统一检查知识库读取、文档写入与知识库管理权限；共享和成员角色决定团队可见范围。会话、私人笔记与学习记录按账号隔离，具体规则见 [API 参考](api.md)。

文档删除进入回收站；上传新版本或对成功文档重新索引时，旧版本保留到新版本处理成功后归档。数据库、原文件和向量存储的职责见 [数据库说明](database.md)，检索流程及能力边界见 [RAG 设计](rag.md)。

