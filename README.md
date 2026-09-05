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

后端和前端启动命令会在实现完成后补充到这里；完整演示流程见 `docs/deployment.md`。

