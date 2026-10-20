# GitHub 发布准备

本轮目标是整理可公开的源码仓库，不包含服务器上线、GitHub 建仓或远程推送。检查日期：2026-10-20。

## 提交范围

保留前后端源码、SQL、启动脚本、示例配置、测试、通用文档和示例截图。以下内容已被 `.gitignore` 排除，原文件仍保存在本机：

- `.env` 及实际环境配置、`.runtime/` 日志和密钥备份。
- `.resume-qa/` 简历、`.report-work/` 课程报告生成资料及根目录课程报告 DOCX。
- `uploads/`、`output/`、`ai/`、IDE 配置、构建产物、依赖目录和私钥文件。

`.env.example` 与 `.env.production.example` 可以提交，真实密钥必须留空或仅保存在部署环境。使用 `git status --short` 检查新增文件，避免强制加入被忽略的私人资料。

历史扫描检查了所有 Git refs 可达的 blob，以及当前已跟踪和未忽略的新增文件；包含常见 provider token、私钥、JWT、带密码 URL，以及本机配置中非示例密钥的精确匹配。初步结果未发现命中。历史中的 `Java-Collections-Guide.pdf` 是仓库脚本生成的公开技术验收样例。扫描不覆盖未引用的 Git 对象、未知格式密钥，也不替代人工审查。

## 可复现性与部署

- Windows 启动器通过 `JAVA_HOME`/PATH 查找 Java，通过 PATH 或显式参数查找 Ollama；不依赖作者电脑的固定目录。
- `.env.production.example` 提供生产模式配置；`prod` 不初始化演示账号，并校验 JWT 和数据库配置。详见 [部署与运行](deployment.md)。
- Compose 端口默认仅绑定本机，公开网站需要部署者配置 HTTPS 入口。
- README 中的三张截图从当前版本实际运行界面截取，展示工作台、书本入口与知识库；使用独立示例账号和三个空的示例知识库，已检查画面不含私人资料。旧版验收截图已移除，不作为准确率或性能证明。

## 验证方式

2026-10-20 本地复验结果：仅复制 Git 当前允许提交的 379 个文件到新的干净目录，不携带 `.env`、依赖目录或构建产物，再安装、测试和构建。

| 检查 | 结果 |
| --- | --- |
| 后端 `mvn verify` | 198 项测试：197 通过、1 条件跳过；打包成功 |
| 前端 `npm ci`、测试、构建 | 安装成功，36 个测试文件、118 项测试通过，生产构建成功 |
| 完整 `npm audit` | 0 项已知漏洞（包括开发依赖） |
| Windows 开发启动器 | 12 项回归通过 |
| Ollama 启动器 | 3 项回归通过；不启动实际模型服务 |
| ECharts 6 兼容性 | 折线、柱状、饼图 SSR 渲染通过 |
| Compose 配置 | 缺少 root 密码时拒绝，显式提供时通过 |
| 敏感信息扫描 | 379 个工作区文件及 490 个历史 blob，所检规则无命中 |
| 独立代码复核 | 发现的 root 密码回退问题已修正，无未解决审查项 |

本轮未执行 Docker 全镜像重建、远程 GitHub Actions 或在线模型端到端测试。前端仍有较大 JS 分包提示，不影响构建；源码公开准备不等同于公网生产环境的全面安全审计。

```powershell
cd SpringBoot
mvn --batch-mode --no-transfer-progress verify
cd ../vue
npm ci
npm run test -- --run
npm run build
npm audit
cd ..
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-startup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-ollama-startup.ps1
```

GitHub Actions 在 push 和 pull request 时执行后端验证、前端安装/测试/构建、npm 高危依赖检查及 Windows 启动器回归。工作流只请求仓库读取权限，不使用模型密钥或生产凭据。首次远程执行需上传仓库后确认。

依赖审查发现的 ECharts 和 source-map-js 问题分别见 [ECharts 公告](https://github.com/advisories/GHSA-fgmj-fm8m-jvvx) 与 [source-map-js 公告](https://github.com/advisories/GHSA-68fv-2mgg-jv7q)。本轮将 ECharts 更新到 6.1.0、Vitest 更新到 4.1.11，并更新相关传递依赖；完整 npm audit 已无已知漏洞，不宣称已完成所有语言生态的漏洞审计。

## 授权与后续

作者尚未选择开源许可证，本次未擅自添加 MIT、Apache 等授权。仓库公开与选择开源许可证是分别处理的决定。依赖及第三方素材的原有许可证继续保留。

收到 GitHub 仓库信息后，再检查最终提交清单、配置远程仓库并按用户要求发布。提交前不改写现有历史、不删除本地私人资料。
