# 数据库说明

MySQL 保存用户、知识库、文档、Chunk、标签、会话、消息、操作日志和 AI 调用日志。Qdrant 只保存向量和 `chunkId`、`documentId`、`knowledgeBaseId`、`pageNumber` 等 payload。删除文档时由后端事务清理 MySQL 记录和本地文件，向量适配器负责删除远程索引。

开发环境可以执行 `docker compose up -d mysql`。数据库脚本统一位于 `sql/`：`init.sql` 用于 MySQL 手动初始化，`schema.sql` 和 `data.sql` 为应用及 H2 测试加载的表结构和初始化数据。Maven 构建自动从 `../sql` 打包后两者到 classpath，源文件不在后端目录重复维护。

