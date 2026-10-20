# API 快速参考

普通业务 JSON 响应使用 `{ code, message, data, timestamp }`；文件下载返回文件流，流式问答使用 SSE。登录后把返回的 Token 放入 `Authorization: Bearer <token>`。示例中的成功返回值均位于 JSON 响应的 `data` 字段中。

公开注册接口（无需登录）：

```http
POST /api/auth/register
Content-Type: application/json

{"username":"new_user","nickname":"新用户","password":"your_password_123","confirmPassword":"your_password_123"}
```

用户名为 4–32 位字母、数字或下划线，昵称选填且最多 50 个字符；密码至少 8 个字符且 UTF-8 编码不超过 72 字节，两次输入必须一致。成功返回 `{ token, user }`，新用户固定为启用状态的 `USER`，不会接受客户端指定的角色或状态。重复用户名返回 409，输入无效返回 400。账号沿用 `sql/schema.sql` 的 `sys_user` 表，密码采用 BCrypt 加密保存。

```http
POST /api/auth/login
Content-Type: application/json

{"username":"student","password":"123456"}
```

```http
POST /api/knowledge-bases
Content-Type: application/json

{"name":"Java 技术","description":"Java 学习资料"}
```

```http
POST /api/knowledge-bases/{id}/documents
Content-Type: multipart/form-data
```

文件字段名为 `file`。HTTP 202 仅表示任务已接收；轮询 `GET /api/documents/{id}`，直到 `status` 为 `SUCCESS` 或 `FAILED`。

```http
GET /api/chat/stream?knowledgeBaseId=1&question=HashMap如何工作
Accept: text/event-stream
```

可传 `sessionId` 延续本人在同一知识库中的会话。客户端使用可携带 Authorization 请求头的流式请求，按事件追加内容：

| SSE 事件 | 数据与含义 |
| --- | --- |
| `meta` | 会话 ID、回答模式和检索信息 |
| `message` | 答案片段，按到达顺序拼接 |
| `citation` | 文档、页码、片段 ID 与摘录 |
| `done` | 统一 Result 包装的会话 ID，表示正常完成 |
| `error` | 错误说明，表示本次问答失败 |

事件以空行分隔，以冒号开头的注释行不属于答案。网络读取可能拆开事件或 UTF-8 字符，应缓存不完整事件并使用流式解码；连接断开但未收到终止事件时不能认定回答完整。

## 知识库可见性与管理权限

管理员可查看和管理全部启用的知识库；普通用户可读取自己创建、已共享或受邀加入的知识库。未共享且未受邀的其他用户个人库不可访问。列表和详情返回 `ownerId`、`ownerUsername`、`ownerNickname`，创建者身份由登录账号确定。

| 身份 | 阅读、下载、问答 | 上传、重新索引、文档删除 | 知识库管理 |
| --- | --- | --- | --- |
| 所有者 / 系统管理员 | 是 | 是 | 是 |
| MANAGER 成员 | 是 | 是 | 是 |
| EDITOR 成员 | 是 | 是 | 否 |
| VIEWER 成员 / 共享库读者 | 是 | 否 | 否 |

## 用户管理资料接口

仅管理员可调用 `GET /api/admin/users/{id}` 查看用户资料，调用 `PUT /api/admin/users/{id}` 修改 `username`、`nickname`、`role`、`status`。详情返回创建与更新时间，不返回密码。用户名重复返回 409，非法输入或禁用/降级当前管理员返回 400，不存在的用户返回 404。修改角色或启用状态后，用户旧登录会话立即失效；修改用户名或昵称保留会话。

用户资料更新支持可选 `password`：省略或空字符串保留原密码，提供时要求至少 8 位且 UTF-8 编码不超过 72 字节，以 BCrypt 保存并撤销旧会话。用户编辑界面角色为只读；资料更新和旧角色接口均禁止把普通用户提升为管理员。

### 知识库共享

创建和更新知识库支持 `shared` 布尔字段。所有账号默认创建私有知识库，可在编辑时开启或取消共享；管理员创建的库也遵守 shared 设置。省略 `shared` 的更新保留原状态。响应包含 `shared`、`sharingLocked`（兼容字段，始终 false）、`starred`、`starCount` 和发布者 `ownerUsername` / `ownerNickname`。个人库对所有者、管理员和受邀成员开放；共享库对登录用户可读。文档编辑允许 EDITOR/MANAGER，知识库编辑与删除允许所有者、管理员和 MANAGER。
## 功能扩展接口

以下接口均需要登录，响应沿用统一 `Result` 格式。`{kb}` 为知识库 ID。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/search?knowledgeBaseId={kb}&q=关键词` | 全文搜索；可选 fileName、fileType、tag、offset、limit（最多 100） |
| `PUT /api/chat/messages/{id}/feedback` | 会话本人提交反馈和原因 |
| `GET /api/feedback/issues?knowledgeBaseId={kb}&status=OPEN` | 管理者读取问题队列，可选 offset |
| `PUT /api/feedback/issues/{id}` | 管理者解决或重新打开反馈 |
| `GET /api/documents/{id}/versions` | 版本记录 |
| `POST /api/documents/{id}/versions` | multipart 上传新版本 |
| `GET /api/knowledge-bases/{kb}/trash` | 回收站 |
| `POST /api/documents/{id}/restore` | 恢复文档；版本冲突返回 409 |
| `GET /api/knowledge-bases/{kb}/permissions` | 当前用户读写管理权限 |
| `GET /api/knowledge-bases/{kb}/members` | 管理者读取成员 |
| `PUT /api/knowledge-bases/{kb}/members` | 按 username 设置 VIEWER、EDITOR、MANAGER 角色 |
| `DELETE /api/knowledge-bases/{kb}/members/{userId}` | 移除成员 |
| `GET/POST /api/knowledge-bases/{kb}/content` | 读取或新增 FAQ、TERM、READING 内容 |
| `PUT/DELETE /api/knowledge-bases/{kb}/content/{id}` | 更新或删除维护内容；PUT 必须携带读取时的 `baseVersion`，缺失返回 400、版本过期返回 409 |
| `POST /api/knowledge-bases/{kb}/retrieval/debug` | `{question,topK}`；topK 为 1～20 |
| `POST /api/knowledge-bases/{kb}/retrieval/evaluate` | `{topK,cases:[{question,expectedDocumentIds}]}`；1～30 条，空来源表示应拒答 |
| `GET /api/knowledge-bases/{kb}/insights?days=30` | 管理者读取问答质量汇总与匿名知识缺口；days 为 1～180 |
| `GET /api/knowledge-bases/{kb}/retrieval/runs` | 管理者读取该知识库最近 20 次评测记录 |
| `GET/POST /api/knowledge-bases/{kb}/web-sources` | 读取网页来源或提交 `{url}` 导入 |
| `POST /api/knowledge-bases/{kb}/web-sources/{id}/check` | 手动检查更新 |
| `POST /api/study/{id}/review-plan` | 从自己的学习记录创建复习计划，重复调用不重复添加 |
| `GET /api/review?all=false` | 到期卡片；all=true 包括将来卡片，最多 200 条 |
| `POST /api/review/{id}` | `{rating:"AGAIN"}`，支持 GOOD、EASY；尚未到期返回 409 |


## 问答质量与可追溯

- `GET /api/chat/stream`（流式问答）的 `meta` 事件与 `GET /api/chat/sessions/{id}/messages` 均返回 `retrieval` 字段：`{rewrittenQuery, topK, sufficient, latencyMs, chunks:[{chunkId, documentId, pageNumber, vectorScore, keywordScore, hybridScore}]}`。日常对话（SMALL_TALK）没有该字段。它记录实际送入回答的片段，便于解释「为什么这样回答」。
- `GET /api/knowledge-bases/{kb}/insights` 返回 `{days, answeredCount, insufficientCount, helpfulCount, notHelpfulCount, openIssueCount, resolvedIssueCount, gaps:[{question, askCount, userCount, lastAskedAt}], hiddenGapCount}`。`gaps` 只包含**至少两名不同用户**提问过的「资料不足」问题，不返回提问者身份；只被单个用户提问过的数量记入 `hiddenGapCount`。需要管理者权限。
- `POST /api/knowledge-bases/{kb}/retrieval/evaluate` 的结果会写入 `retrieval_evaluation_run` 表，响应新增 `id`、`datasetHash`（评测集内容哈希，相同即同一套标注问题）、`createdAt`。`GET .../retrieval/runs` 返回同样的汇总字段，用于比较不同时间的检索质量。
- `GET /api/dashboard/recent` 返回 `{sessions, documents, failedDocuments, dueReviewCount}`，只包含当前用户可访问的知识库；`sessions` 为本人会话。

部署和能力边界见 [功能扩展说明](feature-update-2026-09-20.md)。
# 问答模型选择（2026-10）

认证后的 `GET /api/system/chat-models` 返回 `{ defaultProvider, options: [{ id, label, model, configured }] }`，不返回密钥或服务地址。`configured` 仅表示配置齐全，不是健康检查。

`GET /api/chat/stream` 新增可选参数 `provider=local|cloud`；缺省使用默认服务。未启用本地模式的旧部署使用 `provider=default`，保持原默认模型。非法来源、云端未配置直接报错，不回退。嵌入和检索配置保持共享。

SSE 新增 `status` 文本事件用于排队/准备提示。完成时的 `meta` 新增可空 `model` 字段，消息历史也返回该字段。生成模式为 `LOCAL_MODEL` 或 `CLOUD_API`；旧 `LOCAL_DEMO` / `OPENAI_COMPATIBLE`、`SMALL_TALK`、`INSUFFICIENT_CONTEXT` 仍兼容。流中的错误保持 `error` 事件；HTTP 402 上游错误转为安全的余额不足提示，不透传原始错误正文。

共享、收藏、修改建议与条目历史 API 见 [共享与协作](collaboration.md)。
