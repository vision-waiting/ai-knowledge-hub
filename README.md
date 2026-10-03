# AI Knowledge Hub

基于 Vue 3 + Spring Boot + RAG 的知识管理与问答系统，支持文档上传、索引处理、流式问答、引用原文、会话恢复和按账号授权访问。

RAG 入库与问答全流程、句段切片、检索去重和上下文预算配置见 [RAG 设计文档](docs/rag.md)。

## 当前状态

代码目录：`vue/` 存放前端，`SpringBoot/` 存放后端，`sql/` 统一存放数据库 SQL 脚本。后端 Java 实体、Mapper 和数据库升级逻辑仍属于后端代码。构建时自动将所需 SQL 加入后端资源；请保持这三个目录位于同一项目根目录。

工作台显示当前账号实际可访问的知识库与统计数据。内置资料可通过认证接口导入为三个可维护的知识库：平台使用手册、文档接入规范、开发接口知识库。文档、索引与会话持久保存，可继续上传自己的资料。

## 知识工具

新增全文搜索、问答反馈闭环、团队角色、文档版本与回收站、FAQ/术语/阅读顺序、检索实验室、网页导入、可选 PDF OCR 和间隔复习。入口、部署配置与能力边界见 [功能扩展说明](docs/feature-update-2026-09-20.md)。

- **文档总结与对比**：从侧边栏或知识库详情进入，选择 1～5 份文档总结，或 2～5 份文档对比，可填写关注点。结果自动保存，也可存为个人笔记。仅可选择已处理完成的文档；长资料会显示实际使用范围。
- **学习助手**：选择资料生成知识卡片和 3～10 道选择题，提交后查看评分与解析；错题自动保存，可标记已掌握或重新练习。学习记录和提交记录保存在服务器。
- **个人笔记**：支持新建、分类、搜索、Markdown 编辑和预览。回答、总结与知识卡片可以保存为笔记。笔记仅本人可访问，管理员也不能查看他人的私人笔记。
- **更新提醒**：在知识库详情点击「关注更新」，之后的文档上传、重新索引、处理成功或失败、删除会显示在站内提醒中。支持未读角标、仅看未读和标记已读；取消关注后不再收到新提醒。

这些功能沿用已有 AI 配置。未配置语言模型时，总结/对比明确展示原文整理，学习助手提供原文卡片与填空练习；已配置模型时生成分析与学习内容。模型不可用或返回格式不合法时显示失败，保留所选资料供重试。

新增数据表由启动时的增量建表自动创建，现有文档和会话不会被清空。`sql/schema.sql` 包含完整建表语句，`sql/notes.sql`、`sql/intelligence.sql`、`sql/notifications.sql` 为各模块的独立建表脚本。

## 环境要求

- Node.js 20+
- Java 21（本机可用 `D:\java\java21`）
- Maven 3.9+
- Docker Desktop（可选，用于 MySQL/Redis/Qdrant/MinIO）

## 启动基础服务

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d mysql redis qdrant minio
```

后端：

```powershell
$env:JAVA_HOME='D:\java\java21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd SpringBoot
mvn spring-boot:run
```

前端另开终端执行：

```powershell
cd vue
npm run dev
```

也可以使用一键开发脚本（自动等待 MySQL、Redis、Qdrant，并在端口被占用时选择下一个端口）：

Windows 下直接双击根目录的 `start-dev.bat`：检查项目和开发工具，必要时启动 Docker Desktop，构建并启动前后端，待页面可访问后自动打开浏览器。重复双击会打开已运行的系统；启动中再次双击会提示等待。失败时窗口保留错误信息，日志位于 `.runtime/`。需要先安装 Java 21、Maven、Node.js 和 Docker Desktop；脚本不会自动安装这些工具。

如果前端或后端单独退出，再次运行启动脚本会保留健康服务，并在原端口补启动缺失服务。恢复端口被其他进程占用时会明确提示端口号。启动脚本回归测试可运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-startup.ps1`，测试使用模拟进程，不会停止实际服务。

```powershell
.\start-dev.ps1
# 无外部模型时使用原文检索（保留历史参数名）
.\start-dev.ps1 -DemoAi
# 使用自己已经配置好的数据库等服务，跳过 Docker 启动
.\start-dev.ps1 -SkipInfrastructure
# 在 PowerShell 中启动后自动打开浏览器
.\start-dev.ps1 -OpenBrowser
```

首次运行会从 `.env.example` 创建缺失的 `.env`，并在缺少 `vue/node_modules` 时执行 `npm ci`。默认启动 MySQL、Redis 和 Qdrant，后端数据库连接自动使用 `MYSQL_DOCKER_PORT`（默认 `3307`）；`-SkipInfrastructure` 则保留 `.env` 中的数据库连接配置。MinIO 是可选服务，本脚本不启动它。中文目录下自动选择有效的 Compose 项目名，检测到现有容器时沿用其项目名和数据卷。

前端默认仅本机访问，地址为 `http://127.0.0.1:5173`；端口冲突时以脚本输出为准。后端构建日志在 `.runtime/backend-build.log`，运行日志在 `.runtime/backend.log` 和 `.runtime/backend-error.log`，前端日志在 `.runtime/frontend.log` 和 `.runtime/frontend-error.log`。重复启动会复用健康进程；修改代码或 `.env` 后，需要先停止对应进程，再运行脚本。

脚本启动后会打印实际访问地址；日志和进程信息保存在 `.runtime/`。容器部署使用 `docker compose up -d --build --wait`，Web 默认端口为 `8088`，可通过 `.env` 中的 `WEB_PORT` 修改。

首次初始化账号：`student / 123456`；管理员：`admin / 123456`。登录页不展示或预填密码。正式使用前在个人中心修改初始密码，部署时配置独立 JWT 密钥和数据库密码。完整架构、API、RAG 和部署说明见 `docs/architecture.md`、`docs/api.md`、`docs/rag.md` 和 `docs/deployment.md`。

## 知识工具

- **文档总结与对比**：在侧栏进入“总结与对比”，选择已处理完成的文档。总结支持 1–5 份，对比支持 2–5 份，可填写关注点；结果自动保存到历史记录，也能存入个人笔记。
- **学习助手**：选择文档生成知识卡片和 3–10 道练习，提交后查看评分和解析；历史练习支持恢复，错题可标记为已掌握。
- **个人笔记**：支持 Markdown 编辑与预览、分类、搜索，以及从问答、总结和学习卡片保存内容。笔记和学习记录按账号隔离。
- **更新提醒**：在知识库详情页关注知识库，之后的上传、重建索引、处理成功或失败、删除会进入站内提醒；支持未读筛选和标记已读。顶部未读数量每分钟刷新，提醒列表支持手动刷新；不发送邮件或系统通知。

生成内容最多使用 20,000 字符的已解析资料，页面会说明实际覆盖范围。在线模式复用当前模型配置；本地演示模式提供明确标注的原文摘录、卡片和填空练习。新增数据表随后端启动自动创建，无须重置数据库。

功能冒烟验证：设置 `KNOWLEDGE_USERNAME`、`KNOWLEDGE_PASSWORD` 后运行 `node scripts/feature-smoke.mjs`。脚本创建并保留示例知识库“功能体验：缓存与消息队列”、生成结果和笔记；在线模式会使用配置的模型服务。

## 注册账号使用说明

新用户可以在登录页点击“创建账号”，或访问 `/register`。注册成功后自动进入工作台，拥有自己的知识库和会话空间；不会获得管理员权限，也不会自动获得其他账号的知识库。已有账号可从注册页返回登录。

## 导入三个知识库

启动系统后在项目根目录执行，将账号和密码替换为实际账号。资料归导入账号所有，管理员也能管理。

```powershell
$env:KNOWLEDGE_API_URL='http://127.0.0.1:8080/api' # 使用实际后端端口
$env:KNOWLEDGE_USER='你的账号'
$env:KNOWLEDGE_PASSWORD='你的密码'
node scripts/import-workspaces.mjs
```

脚本通过正常上传与索引流程导入 `knowledge-data/workspace/` 下的资料。重复执行复用同名知识库和同名文件，不覆盖用户内容。处理失败时可在文档页面修复并重建索引后重试。结果写入 `output/workspaces.json`。

使用相同环境变量执行 `node scripts/verify-workspaces.mjs` 可检查三个知识库的回答、引用归属、历史恢复和原文件下载；会发起三次问答并保留会话，启用在线模型时会产生相应服务用量。

运行状态页 `/api/system/status` 会分别显示 LLM、Embedding、向量库和 Redis 模式。当前 `.env` 允许在线 LLM 与本地 Embedding 组合；若要启用真实向量服务，需要同时设置 `EMBEDDING_ENABLED=true`、`EMBEDDING_BASE_URL`、`EMBEDDING_MODEL` 和 `EMBEDDING_API_KEY`。当前配置的模型服务没有可用 Embedding 模型，因此默认保留本地向量模式。
