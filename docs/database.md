# 数据库说明

## 数据存储职责

MySQL 保存用户、知识库与成员、文档与版本、Chunk、标签、会话与消息、笔记、学习与复习记录、通知、反馈、检索评测、操作日志和 AI 调用日志。启用 Qdrant 时，它保存向量和 `chunkId`、`documentId`、`knowledgeBaseId`、`pageNumber` 等 payload；上传原文件保存在 `UPLOAD_DIR` 指定的目录中。

## 初始化与升级

开发环境可以执行 `docker compose up -d mysql`。数据库脚本统一位于 `sql/`：`init.sql` 用于 MySQL 手动初始化，`schema.sql` 和 `data.sql` 为应用及 H2 测试加载的表结构和初始化数据。Maven 构建自动从 `../sql` 打包后两者到 classpath，源文件不在后端目录重复维护。

`SchemaMigration` 启动时执行 `schema.sql` 并补充缺失字段；正常升级无需重置数据库。各模块独立 SQL 文件用于查阅和按需维护，应用运行时的完整表结构以 `schema.sql` 与迁移代码为准。升级前应备份数据。

## 文档版本与删除

- 删除单个文档将状态设为 `TRASHED`，保留原文件、片段和标签供恢复及历史追溯；检索仅使用 `SUCCESS` 文档。
- 对成功文档重新索引或上传新版本，会先处理新记录；新版本成功后，由 `DocumentVersionActivation` 在数据库事务内将旧版本改为 `ARCHIVED`、新版本改为 `SUCCESS`。新版本失败不会提前替换原来的成功版本。
- 回收站恢复可能因版本冲突返回 409。删除整个知识库与单个文档移入回收站是不同操作，不能依赖文档回收站恢复已删除的知识库。

数据库事务不能原子回滚文件系统或远程 Qdrant 操作。备份和恢复应同时考虑 MySQL、上传目录、Qdrant 数据及对应模型配置，避免只恢复数据库后出现原文件丢失或向量维度不匹配。

# 问答模型记录（2026-10）

`chat_message.model VARCHAR(255) NULL` 保存每条生成回答实际使用的模型；无模型调用的回复与旧记录为空。启动迁移只追加字段，保留历史数据。`ai_usage_log.model` 同步记录本次模型，`mode` 区分 `LOCAL_MODEL` / `CLOUD_API`。主初始化脚本与升级迁移均已更新。

## 共享协作增量

新增 knowledge_star、content_suggestion、content_revision 三表与 knowledge_content.version 列，启动自动升级。shared 由显式设置决定，启动不再强制公开管理员的知识库。已有数据与分享状态保留；详见 [共享与协作](collaboration.md)。
