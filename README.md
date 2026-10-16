# Lucent

Lucent 是一款中文 Android 笔记本应用：笔记、来源文档和对话记录保存在设备本地，并可按笔记本调用云端大模型进行问答。

> 当前界面与文案以简体中文为主。项目版本号由 `app/build.gradle.kts` 中的 `MARKETING_VERSION` 管理。

## 当前功能

- **笔记本**：创建、重命名、删除和拖动整理笔记本。
- **来源文档**：每个笔记本维护一份本地来源文档，支持导入 `.txt`、`.doc` 和 `.docx`，导入后转换为 Markdown；可在来源页阅读和定位内容。
- **本地知识库问答**：在笔记本的“对话”页向云端模型提问，使用该笔记本来源作为上下文；支持流式回答、思考过程、来源引用、回答统计、复制、编辑、重新生成和删除。
- **模型与 API 方案**：保存多组 BYOK 配置，支持 OpenAI 兼容接口、Anthropic、Google 和自定义地址；可选择方案和模型，并测试连接、获取模型。
- **显示设置**：浅色、深色、跟随系统、动态取色和纯白模式；可设置背景、动画、字体、字号、行距、字距。
- **本地存储**：Room 数据库和 DataStore 保存笔记、对话、配置和显示设置；应用不提供云端同步。API 密钥保存在本地，应用当前不对数据库内容做额外加密。

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- Room、DataStore、OkHttp/SSE
- Haze 毛玻璃效果
- Android API 28+，目标 SDK 36
- 模块：`app` 和用于启动基线画像的 `baselineprofile`

## 本地检查

本地允许编译、单元测试、Lint 和源码检查，但不在本地打包 APK：

    python tools/verify.py quick
    python -m unittest discover -s tools/tests -v
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:testDebugUnitTest

完整的 Debug/Release 编译、JVM 测试、Lint、Room schema、插桩测试和 APK 构建由 GitHub Actions 执行：

- [check workflow](.github/workflows/check.yml)
- [Build APK only workflow](.github/workflows/build-apk.yml)

推送到 `main` 会触发这两个工作流。APK 工作流优先使用仓库 Secrets 中的正式签名；未配置时会使用仓库内 `debug.keystore`，产物可安装但不是正式发布签名。

## 项目结构

    app/             Android 应用、Compose 界面、Room 数据层和本地资源
    baselineprofile/ 启动基线画像模块
    tools/           文案生成、源码检查和测试脚本
    .github/         CI 与 APK 构建工作流

## 许可与第三方声明

Lucent 自有代码的许可边界以仓库当前配置为准；项目使用的第三方组件、Androidify 代码改编来源及 Apache 许可全文见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。Androidify 改编源码本身也保留了许可证头，APK 内含对应的 license 资源。

## 当前边界

- 应用是本地优先的单设备笔记本，不包含账号、云同步或多人协作。
- 大模型请求需要用户自行配置 API 地址、密钥和模型，并受服务商协议与网络影响。
- 真机视觉、厂商 ROM、后台保活和真实模型端点不由本地源码检查完全覆盖。
