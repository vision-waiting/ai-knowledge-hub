# API 快速参考

所有 REST 响应使用 `{ code, message, data, timestamp }`。登录后把返回的 Token 放入 `Authorization: Bearer <token>`。

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

```http
GET /api/chat/stream?knowledgeBaseId=1&question=HashMap如何工作
Accept: text/event-stream
```


## 知识库可见性与管理权限

管理员可查看和管理全部启用的知识库；普通用户可读取自己或当前管理员创建的知识库，不能读取其他普通用户的知识库。管理员创建的知识库对普通用户提供文档阅读、下载、问答和关注更新；上传、重新索引、修改和删除仅限创建者或管理员。列表和详情返回 `ownerId`、`ownerUsername`、`ownerNickname`，管理界面显示创建者。创建者身份始终由登录账号确定。

## 用户管理资料接口

仅管理员可调用 `GET /api/admin/users/{id}` 查看用户资料，调用 `PUT /api/admin/users/{id}` 修改 `username`、`nickname`、`role`、`status`。详情返回创建与更新时间，不返回密码。用户名重复返回 409，非法输入或禁用/降级当前管理员返回 400，不存在的用户返回 404。修改角色或启用状态后，用户旧登录会话立即失效；修改用户名或昵称保留会话。

用户资料更新支持可选 `password`：省略或空字符串保留原密码，提供时要求至少 8 位且 UTF-8 编码不超过 72 字节，以 BCrypt 保存并撤销旧会话。用户编辑界面角色为只读；资料更新和旧角色接口均禁止把普通用户提升为管理员。

### 知识库共享

创建和更新知识库支持 `shared` 布尔字段。普通用户默认创建个人知识库，可在编辑时开启或取消共享；管理员创建的知识库强制共享。省略 `shared` 的更新保留原状态。响应包含 `shared`、`sharingLocked` 和发布者 `ownerUsername` / `ownerNickname`。个人库对所有者、管理员和受邀成员开放；共享库对登录用户可读。文档编辑允许 EDITOR/MANAGER，知识库编辑与删除允许所有者、管理员和 MANAGER。
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
| `PUT/DELETE /api/knowledge-bases/{kb}/content/{id}` | 更新或删除维护内容 |
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

- `POST /api/chat/sessions/{id}/messages`（流式问答）的 `meta` 事件与 `GET /api/chat/sessions/{id}/messages` 均返回 `retrieval` 字段：`{rewrittenQuery, topK, sufficient, latencyMs, chunks:[{chunkId, documentId, pageNumber, vectorScore, keywordScore, hybridScore}]}`。日常对话（SMALL_TALK）没有该字段。它记录实际送入回答的片段，便于解释「为什么这样回答」。
- `GET /api/knowledge-bases/{kb}/insights` 返回 `{days, answeredCount, insufficientCount, helpfulCount, notHelpfulCount, openIssueCount, resolvedIssueCount, gaps:[{question, askCount, userCount, lastAskedAt}], hiddenGapCount}`。`gaps` 只包含**至少两名不同用户**提问过的「资料不足」问题，不返回提问者身份；只被单个用户提问过的数量记入 `hiddenGapCount`。需要管理者权限。
- `POST /api/knowledge-bases/{kb}/retrieval/evaluate` 的结果会写入 `retrieval_evaluation_run` 表，响应新增 `id`、`datasetHash`（评测集内容哈希，相同即同一套标注问题）、`createdAt`。`GET .../retrieval/runs` 返回同样的汇总字段，用于比较不同时间的检索质量。
- `GET /api/dashboard/recent` 返回 `{sessions, documents, failedDocuments, dueReviewCount}`，只包含当前用户可访问的知识库；`sessions` 为本人会话。

部署和能力边界见 [功能扩展说明](feature-update-2026-09-20.md)。
