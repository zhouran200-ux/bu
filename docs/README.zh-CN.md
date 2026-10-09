<div align="center">

# 𝓛𝓾𝓬𝓮𝓷𝓽

### 现代 · 极简 · 内敛至近乎沉默

**一款纯粹专注的 Android 笔记与任务管理应用，内置云端大模型知识库问答——一切数据静栖于设备之上、封存在加密数据库之中。在 GitHub 上一键即可完成构建与签名打包。**

</div>

---

## 总体思路

市面上绝大多数笔记应用，迫你在三样好东西里割舍其一：美观、私密、聪慧——取其二，余者付之阙如。Lucent 不作此交易。

你落笔的一切都封存于 SQLCipher 加密数据库中，寸字不离设备。AI 助手可以连接你所订阅的云端大模型，也可以干脆留空——笔记与待办本身依然完备如初。构建过程无需配置繁杂的本地开发环境，在 GitHub 上点击运行 Actions，即可获得签名的 Release APK。

---

## 纯粹专注的 Android 架构

Lucent 是一个精炼、标准且易于维护的单模块 Android 原生应用：
* **核心框架**：100% Kotlin + Jetpack Compose 响应式声明式 UI。
* **数据存储**：Room + SQLCipher 高强度全量静态加密。
* **网络与 AI**：OkHttp + SSE 流式通信，支持主流云端大语言模型与思考链展示。
* **工程结构**：全部代码内聚于 `app/` 模块中，摒弃多余跨平台层与多模块开销，构建轻盈迅速。

---

## 智能助手与笔记本知识库

Lucent 为笔记库配备了灵活实用的模型问答能力：
* **自带服务商（BYOK）**：轻松配置 OpenAI、Anthropic (Claude)、Google (Gemini)、DeepSeek、Kimi 或自定义兼容接口，支持保存多组配置与一键测试连通性。
* **笔记本知识库（RAG 对话）**：在任意笔记本内唤出助手，助手将实时提取当前笔记本内的所有笔记作为专属上下文，为你答疑解惑、梳理要点、撰写总结。
* **思考过程可视化**：支持 DeepSeek 等推理模型的思考链（Thinking / Reasoning Process）实时流式展开与折叠。
* **隐私网络伪装**：支持全局配置自定义 User-Agent 请求头与预设模式。

---

## 记得自己前世的笔记

* **版本快照回溯**：每一次有意义的编辑都被快照留存，你能精确回溯某条笔记曾经的模样并一键还原。
* **双向链接**：输入 `[[笔记标题]]` 即可生成链接，点击直达关联笔记；不存在的标题会自动标红示警，点击即可顺手创建。
* **随心书写**：支持实时渲染的 Markdown 排版，支持任务复选框清单原地勾选，支持标签分类、个性配色、置顶、富文本与涂鸦画布。
* **专属私密区与草稿箱**：为敏感笔记提供带独立门锁的私密区域，草稿箱则让未竟之思永远不至于沦为弃子。
* **快捷模板**：内置日记、会议、创意、清单四个一键模板，更支持自定义与编辑你自己的专属模板。

---

## 真正有意义的截止日期与任务清单

* **子任务与优先级**：主任务下支持细分检查项，高、中、低优先级分明。
* **自然语言时间解析**：输入“下周五下午3点”，自动识别为精确的时间戳。
* **归档与聚焦**：已完成任务自动迁往专属归档界面，保持主界面清爽专注。

---

## 坦荡磊落，液态流光毛玻璃质感

* **流动的毛玻璃美学**：优雅的渐变色在轻柔的高斯模糊（Haze）面板后方悠然漂移，带来现代通透的视觉体验。
* **丰富的调色盘**：浅色、深色、跟随系统，以及针对 Android 12+ 动态壁纸取色的 Monet 主题色调。

---

## 算数的锁，与你同行的备份

* **防暴力破解应用锁**：递增冷却时间、重启保留计数器、可选安全问题与防窥遮罩。
* **独立加密备份**：一键导出受密码保护的 `.lcb` 全量加密备份文件，包含所有笔记、任务、历史快照、附件与设置，随时跨设备无损迁移恢复。
* **开放导出**：支持将笔记按需批量导出为 Markdown、纯文本或文档，数据永远由你掌控。

---

## 纯粹专注的中文界面

规范考究的简体中文表达——覆盖每一个屏幕、对话框、日期格式与模板。所有文案集中由 `tools/i18n/catalog.py` 严格校验，纯净无冗余。

---

## 构建它（在手机上或云端即可完成）

不需要在本地安装任何 Android SDK 或命令行工具。推送到 GitHub，打开 **Actions**，运行 `build-apk.yml` 流水线，即可下载正确签名的 Release APK。

### 项目结构

```
app/          Android 完整应用模块 — Activity、Jetpack Compose 界面、Room 数据库层与中文资源
tools/        代码生成器、i18n 文案字典管理、代码质量检查脚本
.github/      build-apk.yml（Android APK 自动签名构建流水线）、check.yml（代码静态分析检查）
docs/         项目文档与开源许可声明
```

---

## 感谢这些巨人，我们站在他们肩上

在玻璃表面之下，Lucent 是众多他人卓越工作的结晶。完整文本与版权声明请见 **[`THIRD-PARTY-NOTICES.md`](./THIRD-PARTY-NOTICES.md)**；简版连同我们的感激之情，列于下方：

| 借来的才华 | 负责的工作 | 许可证 |
|---|---|---|
| [Kotlin](https://github.com/JetBrains/kotlin) & [Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 核心编程语言，以及优雅的异步协程 | Apache-2.0 |
| [Jetpack Compose & AndroidX](https://developer.android.com/jetpack/androidx) | Android 声明式现代化界面组件库 | Apache-2.0 |
| [Material Icons](https://github.com/google/material-design-icons) | 规范生动的系统矢量图标 | Apache-2.0 |
| [Haze](https://github.com/chrisbanes/haze) — © Chris Banes | 通透灵动的毛玻璃高斯模糊渲染 | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) — © Square, Inc. | 稳定高效的 HTTP/SSE 网络请求通信 | Apache-2.0 |
| [SQLite](https://www.sqlite.org/) | 数据库引擎，安静高效地保存一切 | Public Domain |
| [SQLCipher](https://www.zetetic.net/sqlcipher/) — © Zetetic LLC | Android 端数据库全量加密安全锁 | BSD-style |

---

## 许可证

Lucent 以 **MIT 许可证** 发布——详见根目录 [`LICENSE`](../LICENSE)。
你可以自由使用、修改、打包或分发该项目。遵循开源协议，请在任何衍生副本中保留原始版权声明与许可说明。
