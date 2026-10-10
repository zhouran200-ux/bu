<div align="center">

# 𝓛𝓾𝓬𝓮𝓷𝓽

### 现代 · 极简 · 内敛至近乎沉默

**一款纯粹专注的 Android 笔记本应用，内置云端大模型知识库问答——一切数据静栖于设备之上、封存在加密数据库之中。在 GitHub 上一键即可完成构建与签名打包。**

</div>

---

## 总体思路

市面上绝大多数笔记应用，迫你在三样好东西里割舍其一：美观、私密、聪慧——取其二，余者付之阙如。Lucent 不作此交易。

你落笔的一切都封存于 SQLCipher 加密数据库中，寸字不离设备。AI 助手可以连接你所订阅的云端大模型，也可以干脆留空——记事与整理本身依然完备如初。构建过程无需配置繁杂的本地开发环境，在 GitHub 上点击运行 Actions，即可获得签名的 Release APK。

---

## 纯粹专注的 Android 架构

Lucent 是一个精炼、标准且易于维护的 Android 原生应用：
* **核心框架**：100% Kotlin + Jetpack Compose 响应式声明式 UI。
* **数据存储**：Room + SQLCipher 高强度全量静态加密。
* **网络与 AI**：OkHttp + SSE 流式通信，支持主流云端大语言模型与思考链展示。
* **工程结构**：应用代码全部内聚于 `app/` 模块，另有一个只负责启动基线画像的 `baselineprofile/` 模块，摒弃多余跨平台层与模块开销，构建轻盈迅速。

---

## 智能助手与笔记本知识库

Lucent 为笔记库配备了灵活实用的模型问答能力：
* **自带服务商（BYOK）**：一键套用 ChatGPT、Claude、Gemini、DeepSeek、Kimi 的预设接口，也可填入任意兼容的自定义地址与协议；支持保存多组方案、长按拖动排序，并在设置里直接测试连通性。
* **笔记本知识库（RAG 对话）**：在笔记本内唤出助手，助手会实时取用该笔记本内的全部笔记作为专属上下文，为你答疑解惑、梳理要点、撰写总结；无需预先建立索引，笔记改了即刻生效。
* **文档来源与预览**：笔记本可上传文档作为来源，来源页会显示每篇的开头摘要与字数，点击即可预览全文。一篇文档只属于一个笔记本，删除笔记本时它的文档与聊天记录一并清除。
* **每本独立的对话设定**：思考强度、温度等模型参数、携带的历史轮数与自定义提示词都按笔记本各自保存，换一本即刻换一套，绝不串台。
* **思考过程可视化**：支持 DeepSeek 等推理模型的思考链（Thinking / Reasoning Process）实时流式展开与折叠。
* **隐私网络伪装**：支持全局配置自定义 User-Agent 请求头与预设模式。

---

## 写字的地方，安静也顺眼

* **笔记本即工作台**：主页是整洁的笔记本网格，支持置顶与拖动整理封面顺序；每本笔记本各有「来源」与「对话」两页，互不干扰。
* **随手补一篇**：来源页可手动添加关联文档，也可从本地选取 `.txt`、`.docx` 文件，自动解析为 Markdown 正文再入库。
* **Markdown 就地排版**：标题、有序与无序列表、引用、代码块、分割线，以及行内的粗体、斜体、删除线与行内代码，在阅读视图里直接呈现，无需切换模式。
* **寸字不离设备**：笔记正文与聊天记录存放在 SQLCipher 加密数据库中；接口密钥、字体索引等敏感项另以密文落盘，全程无需服务器。

---

## 坦荡磊落，液态流光毛玻璃质感

* **流动的毛玻璃美学**：优雅的渐变色在轻柔的高斯模糊（Haze）面板后方悠然漂移，带来现代通透的视觉体验。
* **丰富的调色盘**：浅色、深色、跟随系统，以及针对 Android 12+ 动态壁纸取色的 Monet 主题色调。

---

## 设置里真的能改

* **外观**：主题（浅色 / 深色 / 跟随系统 / Monet 动态取色）、背景（调色盘、轮换与动画开关）、字体与排版，各占一页。
* **编辑器**：链接开关与全局文本选择，两项都确实作用在界面上。
* **API 配置**：多组方案保存、长按拖动排序、模型获取与一键测试连通性。
* **简体中文单语界面**：文案集中在 `tools/i18n/catalog.py`，不提供语言切换。

---

## 纯粹专注的中文界面

规范考究的简体中文表达——覆盖每一个屏幕与对话框，日期与数量格式亦在其内。所有文案集中由 `tools/i18n/catalog.py` 严格校验，只留用得上的条目，没有第二种语言的空壳。

---

## 构建它（在手机上或云端即可完成）

不需要在本地安装任何 Android SDK 或命令行工具。推送到 GitHub，打开 **Actions**，运行 `build-apk.yml` 流水线，即可下载正确签名的 Release APK。

### 项目结构

```
app/             Android 应用模块 — Activity、Jetpack Compose 界面、Room 数据库层与中文资源
baselineprofile/ 启动基线画像模块 — 生成首屏 profile，让冷启动更利落
tools/          代码生成器、i18n 文案字典管理、代码质量检查脚本
.github/        build-apk.yml（Android APK 自动签名构建流水线）、check.yml（代码静态分析检查）
docs/           项目文档与开源许可声明
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
