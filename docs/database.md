# 数据库说明

MySQL 保存用户、知识库、文档、Chunk、标签、会话、消息、操作日志和 AI 调用日志。Qdrant 只保存向量和 `chunkId`、`documentId`、`knowledgeBaseId`、`pageNumber` 等 payload。删除文档时由后端事务清理 MySQL 记录和本地文件，向量适配器负责删除远程索引。

开发环境可以执行 `docker compose up -d mysql`，初始化脚本位于 `sql/init.sql`。测试使用 H2 MySQL 模式和 `backend/src/main/resources/schema.sql`。

