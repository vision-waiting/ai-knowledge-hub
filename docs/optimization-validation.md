# 优化与验证边界

## 已落地

- 背景桌面 WebP 302674 字节、手机 WebP 99826 字节；原始 PNG 3116194 字节保留供维护。767px 以下加载手机图。
- Markdown 保留结构；切片优先保护容量内完整的三反引号/波浪线代码块及竖线表格。超过容量仍拆分。PDF 跨页拼接尚未改动，避免丢失引用页码。
- `scripts/fixtures/retrieval-baseline.json` 包含 50 道项目资料构造题，并非真实用户问题；追问提供独立检索形式，因此不代表多轮会话评测。
- `scripts/evaluate-retrieval.mjs` 记录 Hit@5、倒数排名、拒答、逐题候选与 p50/p95 请求延迟；`scripts/compare-retrieval.mjs` 在捕获候选内对比原排序和 RRF。候选内对比不能证明全量召回效果，生产排序不变。
- `scripts/reading-browser-regression.js` 与 CI 工作流覆盖速度记忆、专注模式、键盘焦点、手机溢出和路由动画。使用模拟接口，不代表真实上传/问答链路验收。

## 复现检索评测

先向测试知识库导入 `knowledge-data/workspace/文档接入规范.md`、`平台使用手册.md`、`knowledge-data/spring/Spring-Boot-guide.md`、`knowledge-data/database/Redis-guide.md`，等待 SUCCESS。

通过环境变量设置 `EVAL_API_URL`、`EVAL_KB_ID`、`EVAL_TOKEN`，运行：

```sh
node scripts/evaluate-retrieval.mjs
node scripts/compare-retrieval.mjs retrieval-report.json
```

令牌不会写入报告。报告包含资料片段，应保存在受控位置；默认输出路径可用 EVAL_OUTPUT 修改。工具仅请求 debug 接口，不上传资料或更改知识库。需要当前知识库管理权限。

人工核查每道题的回答、引用原文与预期来源，逐条标记“支持／部分支持／不支持”；不能将关键词命中当成答案被证据支持。

不同数据规模的生产检索基准、真实模型答案支持率、PDF 跨页结构及真实上传到引用的浏览器端到端流程仍需要单独验证；本次未生成这些指标，未宣称已验收。
