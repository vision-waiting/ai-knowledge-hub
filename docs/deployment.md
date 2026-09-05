# 部署与演示

## 本地启动

```powershell
Copy-Item .env.example .env
docker compose up -d mysql redis qdrant minio
$env:JAVA_HOME='D:\java\java21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
mvn spring-boot:run
```

另开终端：

```powershell
cd frontend
npm run dev
```

打开 `http://localhost:5173`，演示账号为 `student / 123456`，管理员账号为 `admin / 123456`。默认 AI 模式为本地演示，不需要 API Key。真实 OpenAI 兼容服务需要在 `.env` 中配置 `LLM_ENABLED=true`、`LLM_API_KEY`、`LLM_BASE_URL` 和模型名。

## 答辩路径

登录 → 创建知识库 → 上传 Markdown/TXT/PDF → 查看状态时间线 → 提问 → 展示 SSE 回答和 Citation → 点击引用 → 提问不存在内容 → 展示拒答 → 管理员查看统计。

