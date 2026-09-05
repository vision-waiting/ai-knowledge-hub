# API 快速参考

所有 REST 响应使用 `{ code, message, data, timestamp }`。登录后把返回的 Token 放入 `Authorization: Bearer <token>`。

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

