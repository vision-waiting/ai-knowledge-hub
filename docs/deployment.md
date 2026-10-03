# 部署与运行

## Windows 开发环境

需要 Java 21、Maven、Node.js 20+ 和运行中的 Docker Desktop。在项目根目录执行：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
.\start-dev.ps1
```

脚本读取 `.env`，启动并等待 MySQL、Redis、Qdrant，构建后端并启动前端。访问终端打印的地址，端口冲突时自动选择可用端口。日志在 `.runtime/`。

## 容器部署

1. 在 `.env` 配置数据库密码、独立 `JWT_SECRET`、模型地址和密钥。保留现有配置，不要用模板覆盖。
2. 执行 `docker compose up -d --build --wait`。访问 `http://localhost:8088`，端口可通过 `WEB_PORT` 修改。
3. 用初始化管理员账号登录，在个人中心修改密码，并在用户管理中维护实际用户。普通账号只能访问自己的知识库。
4. 需要内置资料时，按 README 的导入步骤设置 API 地址为 `http://localhost:8088/api` 后执行脚本。

停止容器使用 `docker compose stop`，恢复使用 `docker compose start`。数据库、上传文件及向量索引通过挂载持久保存；备份时需同时覆盖这些数据。公网服务应由部署环境提供 HTTPS。

## 问答模式

`LLM_ENABLED=true` 并配置 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL` 后使用在线模型。向量模型单独通过 `EMBEDDING_ENABLED` 及对应参数配置；在线问答可搭配本地向量。前端显示“AI 问答 · 本地向量”时，本地向量与专业语义向量的检索效果存在差异。

未启用大语言模型时，系统执行原文检索，返回文档摘录并标明模式。`start-dev.ps1 -DemoAi` 是兼容保留的命令参数，对应此原文检索模式。模型服务出错会明确返回错误，不将失败伪装成生成成功。

## 可重复验收

后端：在 `SpringBoot/` 运行 `mvn test`。前端：在 `vue/` 运行 `npm run test -- --run` 和 `npm run build`。

三个知识库实测：在项目根目录运行 `node scripts/verify-workspaces.mjs`，所需账号与地址环境变量见 README。结果保存到 `output/workspaces-verification.json`，不包含密码或令牌。
## 新增网页导入与 OCR 依赖

网页导入需要 curl（默认命令 `curl`，可通过 `CURL_COMMAND` 指定绝对路径）。服务器需能直接访问目标公网 HTTPS 地址；抓取器禁用代理并验证 DNS/重定向地址。

扫描 PDF 的 OCR 为可选能力。本地安装 Tesseract 及中文/英文语言包后，设置 `OCR_COMMAND` 为可执行文件路径、`OCR_LANGUAGES=chi_sim+eng`；留空则不启用。Dockerfile 已声明安装相关包，但部署前仍需构建镜像并用自己的扫描资料校验识别质量。完整限制见 [功能扩展说明](feature-update-2026-09-20.md)。

启动时通过 `SchemaMigration` 创建新增模块表并增量添加版本和反馈字段。升级前备份数据库及上传目录，不要删除旧版本原文件，否则历史引用将无法下载。
