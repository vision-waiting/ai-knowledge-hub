# 本地 AI 配置

本项目通过兼容 OpenAI 的 HTTP 接口连接 Ollama。生成模型、Embedding 和 Qdrant 分别配置；仓库不分发模型权重，也不依赖作者电脑上的程序目录。

## 安装与模型

安装 Ollama，并确保新终端能运行 `ollama --version`。按设备资源选择模型，下列配置使用 `qwen3.5:4b` 和 `qwen3-embedding:0.6b` 作为示例。下载模型需要网络及足够的磁盘空间；性能取决于模型、硬件、上下文长度与并发量。

```powershell
ollama pull qwen3.5:4b
ollama pull qwen3-embedding:0.6b
```

首次使用先复制 `.env.example` 为 `.env`；已有配置不要覆盖。修改以下配置：

```dotenv
LLM_ENABLED=true
LOCAL_LLM_ENABLED=true
LLM_BASE_URL=http://127.0.0.1:11434/v1
LLM_API_KEY=ollama
LLM_MODEL=qwen3.5:4b
LLM_REASONING_EFFORT=none
LLM_MAX_TOKENS=2048
EMBEDDING_ENABLED=true
EMBEDDING_BASE_URL=http://127.0.0.1:11434/v1
EMBEDDING_API_KEY=ollama
EMBEDDING_MODEL=qwen3-embedding:0.6b
QDRANT_ENABLED=true
QDRANT_COLLECTION=knowledge_chunks_qwen3_06b_v1
CONTEXT_MAX_CHARS=3500
```

`ollama` 是本地客户端占位密钥，不是外部 API 凭据。更换生成模型时，确认其支持所选推理参数；不支持时将 `LLM_REASONING_EFFORT` 留空。更换 Embedding 模型应使用新的集合并重新索引，不要混用不同维度或不同模型的向量。

## Windows 启动

```powershell
.\start-local-ai.ps1 -OpenBrowser
```

脚本读取 `.env`，检查本地模型配置，启动或复用 `127.0.0.1:11434` 上的 Ollama，确认模型已下载，再启动项目。不会自动下载模型或覆盖 `.env`。

如 Ollama 不在 PATH 中，可在 `.env` 设置 `OLLAMA_EXECUTABLE` 为 `ollama.exe` 的完整路径；需要复用独立模型目录时设置 `OLLAMA_MODELS`。留空则使用 PATH 中的程序和 Ollama 默认模型目录。

仅启动服务：

```powershell
.\scripts\start-ollama.ps1
# 自定义安装目录时显式传入路径：
.\scripts\start-ollama.ps1 -ExecutablePath 'C:\tools\ollama\ollama.exe' -ModelsPath 'C:\models\ollama'
```

新启动的进程仅监听本机，限制模型并行度为 1、最多加载 2 个模型、等待队列上限为 2，并关闭 Ollama 云功能。这些设置只传给新进程；复用已有服务时保留该服务自身的配置。日志保存在 `.runtime/ollama.log` 和 `.runtime/ollama-error.log`。已有服务应自行确认监听范围和资源配置。

macOS/Linux 可自行启动 `ollama serve`，然后按部署文档启动后端和前端。以上 PowerShell 启动器仅支持 Windows。容器内的 `127.0.0.1` 指向容器自身；容器后端连接宿主机模型时需要可达地址及相应网络配置，不能直接复制上述宿主机地址。

## 可选云端模型

配置 `CLOUD_LLM_BASE_URL`、`CLOUD_LLM_API_KEY`、`CLOUD_LLM_MODEL` 可在聊天页逐问题选择云端模型。云端有独立的 `CLOUD_LLM_REASONING_EFFORT` 和 `CLOUD_LLM_MAX_TOKENS`。密钥只由后端读取。

选择云端会向所配 API 发送本次问题、会话上下文和检索片段；页面会提示。服务失败不会自动改用另一模型。文档总结、学习助手等仍使用默认生成服务。不要把真实密钥、运行日志或模型验收报告放入公开仓库。

## 验证

```powershell
Invoke-RestMethod http://127.0.0.1:11434/api/version
ollama list
ollama ps
$env:SMOKE_API_URL='http://127.0.0.1:8080/api'
$env:SMOKE_USER='你的测试账号'
$env:SMOKE_PASSWORD='你的测试密码'
node scripts/smoke-test.mjs
```

`smoke-test.mjs` 创建并清理自己的临时知识库。`verify-local-ai.mjs` 专用于上方两种示例模型；`verify-dual-model.mjs` 还要求本地为默认聊天模型，配置云端时会调用外部 API。仅验收本地可设置 `ACCEPTANCE_SKIP_CLOUD=true`。这些验收不是普通单元测试，需自行启动服务，并会使用推理资源。报告写入被忽略的 `.runtime/`。

`index-existing-local.mjs` 是特定迁移工具，仅适用于项目默认 Docker MySQL 容器和上述 qwen3 Embedding 配置；不是通用安装步骤。通常使用页面的“重新索引”即可。迁移前备份数据，执行期间避免修改文档。

修改 `.env` 后需重启后端；启动脚本会复用健康进程，不会热加载新配置。
