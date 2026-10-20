# 部署与运行

## Windows 开发环境

需要 Java 21、Maven、Node.js 20+ 和运行中的 Docker Desktop。在项目根目录执行：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
.\start-dev.ps1
```

脚本读取 `.env`，启动并等待 MySQL、Redis、Qdrant，构建后端并启动前端。访问终端打印的地址，端口冲突时自动选择可用端口。日志在 `.runtime/`。

脚本连接 Docker MySQL 时使用宿主机映射端口 `MYSQL_DOCKER_PORT`（默认 3307）；容器内部端口仍为 3306。直接运行 `mvn spring-boot:run` 不会加载根目录 `.env`，需要在终端设置进程环境变量。已有数据库服务可使用 `-SkipInfrastructure`，此时保留 `.env` 中的连接配置。

## 容器开发环境

1. 在 `.env` 配置数据库密码、独立 `JWT_SECRET`、模型地址和密钥。保留现有配置，不要用模板覆盖。
2. 执行 `docker compose up -d --build --wait`。访问 `http://localhost:8088`，端口可通过 `WEB_PORT` 修改。
3. 用初始化管理员账号登录，在个人中心修改密码，并在用户管理中维护实际用户。普通账号可读取自己的库、共享库及受邀加入的库，写入与管理按成员角色授权。
4. 需要内置资料时，按 README 的导入步骤设置 API 地址为 `http://localhost:8088/api` 后执行脚本。

停止容器使用 `docker compose stop`，恢复使用 `docker compose start`。数据库、上传文件及向量索引通过挂载持久保存；备份时需同时覆盖这些数据。公网服务应由部署环境提供 HTTPS。

## 生产部署

生产环境使用独立数据库和上传目录，避免直接发布含私人资料的开发环境。新部署先复制 `.env.production.example` 为 `.env`，不要覆盖已有配置。至少设置：

- `SPRING_PROFILES_ACTIVE=prod`，不可同时激活 `dev` 或 `test`。
- 独立随机 `JWT_SECRET`，至少 32 字节；可运行 `node -e "console.log(require('node:crypto').randomBytes(48).toString('hex'))"` 生成，只写入部署环境的 `.env`。
- 独立 `MYSQL_PASSWORD`（至少 12 字符）及不同的 `MYSQL_ROOT_PASSWORD`；应用使用专属非 root 账号。

生产启动会拒绝缺失、过短或内置示例 JWT 密钥，拒绝开发默认数据库密码和 root 数据库账号。配置校验不是密码强度评估，请使用随机值。

模板的 `MYSQL_SSL_MODE=DISABLED`、`MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL=true` 仅适用于同一主机上的 Compose 私有网络。远程数据库应设置 `MYSQL_SSL_MODE=VERIFY_IDENTITY`、`MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL=false` 并配置受信任证书。`prod` 配置在未指定这些变量时默认验证证书。

```powershell
docker compose -p ai-knowledge-hub up -d --build --wait
```

Compose 的 Web 入口为 `http://127.0.0.1:8088`，服务端口默认只绑定本机。公网部署由宿主机 HTTPS 反向代理转发至该入口；不要直接暴露数据库、Redis、Qdrant 或模型接口。可选 MinIO 必须单独设置 `MINIO_ROOT_PASSWORD`，默认不启用。

生产空数据库不创建演示账号。先通过本机入口注册自己的账号，再由数据库管理者确认账号后执行以下初始化（将用户名替换为刚注册的账号）：

```sql
SELECT id, username, role FROM sys_user WHERE username = 'your_registered_username';
UPDATE sys_user SET role = 'ADMIN', token_version = token_version + 1
WHERE username = 'your_registered_username' AND role = 'USER';
```

重新登录后获得管理员权限。不要公开提供注册即管理员的逻辑。若迁移旧开发数据库，切换 profile 不会删除已有账号；必须先修改或停用演示账号、更新 JWT 密钥，并核对数据是否允许公开。`prod` 启动不会重置账号或清空业务数据。

当前数据库升级在启动时执行，因此应用数据库账号需要本项目数据库内的建表/修改表权限。部署前备份，避免授予其他数据库权限。变更已有 MySQL 数据卷的密码必须同时在数据库内修改账号，单改 `.env` 不会重设已有数据库用户密码。

## 问答模式

`LLM_ENABLED=true` 并配置 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL` 后使用兼容 API 的语言模型，服务可位于本机或远程。向量模型单独通过 `EMBEDDING_ENABLED` 及对应参数配置；模型问答可搭配本地 hash 向量，后者不等同于专业语义向量。

Qdrant 存储由 `QDRANT_ENABLED` 独立控制，Compose 后端服务将其设置为 `true`。更换 Embedding 模型时应使用匹配的向量集合并重新索引。Windows 宿主机上的 Ollama 配置见 [本地 AI](local-ai.md)；容器内的 `127.0.0.1` 指向容器自身，不能直接沿用宿主机地址。

未启用大语言模型时，系统执行原文检索，返回文档摘录并标明模式。`start-dev.ps1 -DemoAi` 是兼容保留的命令参数，对应此原文检索模式。模型服务出错会明确返回错误，不将失败伪装成生成成功。

## 可重复验收

后端：在 `SpringBoot/` 运行 `mvn test`。前端：在 `vue/` 运行 `npm run test -- --run` 和 `npm run build`。

三个知识库实测：在项目根目录运行 `node scripts/verify-workspaces.mjs`，所需账号与地址环境变量见 README。结果保存到 `output/workspaces-verification.json`，不包含密码或令牌。

各脚本的环境变量名称不同，运行前按下表设置；均在项目根目录执行：

| 脚本 | API 地址变量 | 账号 / 密码变量 | 数据影响 |
| --- | --- | --- | --- |
| `scripts/import-workspaces.mjs` | `KNOWLEDGE_API_URL` | `KNOWLEDGE_USER` / `KNOWLEDGE_PASSWORD` | 导入并保留资料，同名文件不覆盖 |
| `scripts/verify-workspaces.mjs` | `KNOWLEDGE_API_URL` | `KNOWLEDGE_USER` / `KNOWLEDGE_PASSWORD` | 发起问答并保留会话 |
| `scripts/feature-smoke.mjs` | `KNOWLEDGE_API` | `KNOWLEDGE_USERNAME` / `KNOWLEDGE_PASSWORD` | 创建并保留示例知识库、生成结果与笔记 |
| `scripts/smoke-test.mjs` | `SMOKE_API_URL` | `SMOKE_USER` / `SMOKE_PASSWORD` | 创建并清理自身临时知识库 |

API 地址均包含 `/api`。前三个脚本默认 `http://127.0.0.1:8080/api`，`smoke-test.mjs` 默认端口为 8082，执行时应显式设置为实际地址；使用 Compose Web 入口时通常改为 `http://127.0.0.1:8088/api`。`verify-workspaces.mjs` 还依赖导入脚本生成的 `output/workspaces.json`。调用模型的验证脚本会使用相应模型服务。

## 新增网页导入与 OCR 依赖

网页导入需要 curl（默认命令 `curl`，可通过 `CURL_COMMAND` 指定绝对路径）。服务器需能直接访问目标公网 HTTPS 地址；抓取器禁用代理并验证 DNS/重定向地址。

扫描 PDF 的 OCR 为可选能力。本地安装 Tesseract 及中文/英文语言包后，设置 `OCR_COMMAND` 为可执行文件路径、`OCR_LANGUAGES=chi_sim+eng`；留空则不启用。Dockerfile 已声明安装相关包，但部署前仍需构建镜像并用自己的扫描资料校验识别质量。完整限制见 [功能扩展说明](feature-update-2026-09-20.md)。

启动时通过 `SchemaMigration` 创建新增模块表并增量添加版本和反馈字段。升级前备份数据库及上传目录，不要删除旧版本原文件，否则历史引用将无法下载。
