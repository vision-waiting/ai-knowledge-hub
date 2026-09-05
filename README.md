# AI Knowledge Hub

基于 Vue 3 + Spring Boot + RAG 的企业智能知识库问答平台，面向本科毕业设计演示。

## 当前状态

项目按 `docs/superpowers/plans/2026-09-05-ai-knowledge-hub-v1.md` 分阶段实现。默认开发模式不依赖真实 LLM，后续可通过 `.env` 配置 OpenAI 兼容接口。

## 环境要求

- Node.js 20+
- Java 21（本机可用 `D:\java\java21`）
- Maven 3.9+
- Docker Desktop（可选，用于 MySQL/Redis/Qdrant/MinIO）

## 启动基础服务

```powershell
Copy-Item .env.example .env
docker compose up -d mysql redis qdrant minio
```

后端：

```powershell
$env:JAVA_HOME='D:\java\java21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
mvn spring-boot:run
```

前端另开终端执行：

```powershell
cd frontend
npm run dev
```

默认演示账号：`student / 123456`；管理员：`admin / 123456`。完整架构、API、RAG 和答辩流程见 `docs/architecture.md`、`docs/api.md`、`docs/rag.md` 和 `docs/deployment.md`。
