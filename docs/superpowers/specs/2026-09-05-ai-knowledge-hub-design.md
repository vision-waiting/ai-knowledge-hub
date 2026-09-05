# AI Knowledge Hub V1 设计规格

## 1. 目标与范围

AI Knowledge Hub 是一个面向企业、实验室和学校团队的知识管理与 RAG 问答平台。本次实现从空目录开始，按照原开发文档的 Vue 3 + Spring Boot 技术方向建立可运行的毕业设计 V1。

V1 交付登录、JWT 认证、角色权限、知识库 CRUD、文档上传与异步处理状态、PDF/TXT/Markdown 解析、文本切块、可插拔 Embedding 与 AI 服务、关键词/向量近似混合检索、带引用的多轮问答、SSE 流式输出、聊天记录、AI 摘要与标签、操作日志、Dashboard 和管理员统计。DOCX/PPTX、Qdrant、Redis、真实 OpenAI 兼容 API 作为同一接口下的可选增强，并提供启动配置。

不在第一轮实现多 Agent、OCR、知识图谱、多租户、工作流引擎、Kubernetes 或微服务拆分。

## 2. 架构决策

### 2.1 技术栈

- 前端：Vue 3、TypeScript、Vite、Element Plus、Vue Router、Pinia、Axios、ECharts、markdown-it。
- 后端：Java 21、Spring Boot 3、Spring Security、JWT、MyBatis-Plus、Maven、Bean Validation。
- 数据：MySQL 保存业务数据，Redis 保存处理状态和限流信息，Qdrant 保存向量；本地 `uploads/` 保存原始文件，生产可切换 MinIO。
- AI：`AiService`、`EmbeddingService`、`DocumentParser`、`Retriever` 使用接口隔离。默认本地演示模式没有外部网络依赖；存在 API Key 时通过 OpenAI 兼容 HTTP 接口切换真实模型。

### 2.2 运行模式

`application-dev.yml` 默认使用本地演示模式和本地文件存储。MySQL 可通过 Docker Compose 启动，也支持连接已有 MySQL。Redis/Qdrant 不可用时分别退化为进程内任务状态和本地检索适配器；退化行为必须在启动日志和 `/api/dashboard/overview` 中可见，不能伪装为真实外部服务。

### 2.3 前后端边界

前端只负责展示、路由和交互，不承担权限判断或检索逻辑。Controller 负责参数接收、校验和统一响应；Service 负责业务流程和权限；Mapper 负责持久化；RAG 模块只接收问题、知识库 ID 和历史消息，返回答案片段及 Citation；文件解析模块通过 `DocumentParser` 选择实现。

## 3. 页面与交互

路由包括：

```text
/login
/dashboard
/knowledge
/knowledge/:id
/knowledge/:id/documents
/knowledge/:id/chat
/chat/history
/profile
/admin/users
/admin/logs
/admin/statistics
```

登录后默认进入 Dashboard。Dashboard 展示知识库、文档、问答、成功率、最近 7 天趋势和“快速提问”入口。快速提问选择知识库后跳转 `/knowledge/:id/chat`。

聊天页采用左侧知识库/会话列表、右侧消息区和底部输入框的布局。回答使用 Markdown 渲染，代码块高亮，引用以 `[1] 文档名 · 第 N 页` 显示并可点击查看 Chunk 原文。支持停止生成、清空会话、重新发送和错误重试。文档页展示文件信息、状态时间线、摘要、标签和重新索引按钮。

## 4. 数据模型

核心表：

| 表 | 作用 |
| --- | --- |
| `sys_user` | 用户、角色、状态、密码哈希 |
| `knowledge_base` | 知识库信息、所有者和文档数量 |
| `document` | 文件元数据、处理状态、摘要 |
| `document_chunk` | 切块文本、页码、向量 ID |
| `document_tag` | AI 生成的关键词 |
| `chat_session` | 用户与知识库会话 |
| `chat_message` | 多轮消息 |
| `operation_log` | 操作审计 |
| `ai_usage_log` | AI 模式、耗时、Token、成功状态 |

所有业务表包含创建和更新时间。`document_chunk` 同时存储 `knowledge_base_id`，查询时必须按知识库过滤。文档删除使用事务，同时删除 Chunk、标签、关联聊天数据、向量和原始文件；清理失败写入日志并标记可重试。

## 5. 文档处理与检索

上传接口只接受 `.pdf`、`.docx`、`.pptx`、`.txt`、`.md`，单文件限制 50 MB。服务端以 UUID 生成存储名，禁止把用户文件名直接拼接为路径。

处理状态：`WAITING`、`PARSING`、`CHUNKING`、`EMBEDDING`、`INDEXING`、`SUCCESS`、`FAILED`。上传请求创建 `WAITING` 记录后立即返回，异步任务依次更新状态。

解析接口定义：

```java
public interface DocumentParser {
    boolean supports(String fileType);
    ParsedDocument parse(InputStream inputStream);
}
```

V1 至少实现 PDF、TXT 和 Markdown，DOCX/PPTX 使用同一接口补齐。切块默认约 800 tokens、100 tokens overlap，优先按标题、段落和句子边界切分；参数在系统配置中可调整。

检索流程：问题重写 → 按 `knowledgeBaseId` 过滤 → 关键词分数与向量分数归一化 → `0.7 * vector + 0.3 * keyword` → 取 Top 3~5 → 相似度阈值判断 → 构建 Prompt。低于阈值返回知识库无足够相关资料，不调用 LLM 补答。

## 6. API 契约

所有接口返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1720000000000
}
```

主要路由：

```text
POST   /api/auth/login
POST   /api/auth/logout
GET    /api/auth/me
GET    /api/dashboard/overview
GET    /api/dashboard/trends
GET    /api/knowledge-bases
POST   /api/knowledge-bases
PUT    /api/knowledge-bases/{id}
DELETE /api/knowledge-bases/{id}
GET    /api/knowledge-bases/{id}/documents
POST   /api/knowledge-bases/{id}/documents
GET    /api/documents/{id}/download
GET    /api/documents/{id}
DELETE /api/documents/{id}
POST   /api/documents/{id}/reindex
GET    /api/chat/sessions
POST   /api/chat/sessions
GET    /api/chat/sessions/{id}/messages
GET    /api/chat/stream
DELETE /api/chat/sessions/{id}
GET    /api/admin/users
PUT    /api/admin/users/{id}/status
GET    /api/admin/logs
GET    /api/admin/statistics
```

聊天 SSE 事件至少包括 `message`、`citation`、`done`、`error`。服务端保存完整回答和引用，断开连接时保存已经生成的内容片段。

## 7. 安全与异常

密码使用 BCrypt，JWT 通过 Spring Security 过滤器解析。每次访问文档、知识库、会话或消息都进行后端归属检查；ADMIN 可访问全部资源，USER 只能操作自己创建的知识库。

统一异常码覆盖未登录、无权限、资源不存在、文件类型不支持、文件过大、处理失败、AI 不可用和检索内容不足。前端显示中文提示，服务端日志保留堆栈和请求上下文。AI 请求按用户限流，默认每分钟 30 次。

## 8. 测试与验收

后端单测覆盖密码校验、权限判断、文件校验、切块、混合排序、阈值和 Citation；API 测试覆盖登录、知识库 CRUD、上传、状态、删除、聊天和管理员接口；前端测试覆盖路由守卫、上传状态、Markdown、引用点击和错误提示；端到端演示覆盖“登录 → 创建知识库 → 上传 → 处理成功 → 提问 → SSE → 引用 → 删除”。

基础验收要求前后端可启动、登录和 JWT 有效、知识库/文档 CRUD 可用、PDF/TXT/Markdown 可解析、本地模式能问答并返回引用、权限有效、删除链路清理完成。完整验收再启用 DOCX/PPTX、Redis、Qdrant、真实 API、摘要标签和管理统计。

论文实验记录关键词、向量、混合三种检索方式，计算 Recall@K、Precision@K 和 MRR。

## 9. 答辩演示路径

登录 → 创建“Java 技术知识库” → 上传资料 → 展示状态时间线 → 提问“Java 中的 HashMap 是如何工作的？” → 展示流式回答和页码引用 → 点击引用查看原文 → 提问知识库中不存在的内容 → 展示低相似度拒答 → 打开管理员统计。

## 10. 明确区分

原开发文档提供技术方向、功能清单、接口示例和编码规则，属于项目需求参考；本规格是结合当前空目录、可用运行环境和用户确认后形成的实际实现约束。若原文档中的“必须外部服务”“必须一次性完成所有增强功能”与本规格的可运行 V1 冲突，以本规格的本地演示模式和分阶段交付为准；这不改变 Vue 3、Spring Boot、MySQL、RAG 和可插拔 AI 适配器的主技术路线。
