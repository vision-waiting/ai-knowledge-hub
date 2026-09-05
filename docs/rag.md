# RAG 设计

问题会先结合最近用户问题做简单重写，然后按 `knowledgeBaseId` 过滤 Chunk。向量分数与关键词分数归一化后按 `0.7 * vector + 0.3 * keyword` 混合，取 Top-K 并执行阈值判断。低于阈值时直接返回知识库无足够资料，不调用 LLM。

本地模式使用固定维度的确定性 token-hash 向量，便于离线答辩演示和实验复现；配置 `LLM_ENABLED=true` 或 `EMBEDDING_ENABLED=true` 后分别切换 OpenAI 兼容适配器。答案引用 Chunk 的文档名、页码、Chunk ID 和片段摘要。

