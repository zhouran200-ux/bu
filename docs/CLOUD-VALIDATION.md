# GitHub 云端验证覆盖表

## 自动执行

推送 main、针对 main 的 Pull Request 或手动运行 check 时，在 GitHub 托管 runner 中执行：

| 类别 | 执行内容 |
| --- | --- |
| 源码/静态脚本 | code_hygiene_check、version_check、settings_cache_check、compose_scroll_check 自检及扫描、design_audit（参考评分） |
| 生成文件 | i18n 重新生成后检查差异 |
| Python 工具测试 | tools/tests 下的 unittest 自动发现，覆盖本地 Gradle 禁止启动、防止缺报告/全跳过被误报成功 |
| Gradle/Kotlin | app Debug、Release Kotlin 编译，Android 插桩测试编译，baselineprofile Debug Kotlin 编译 |
| JVM 单元测试 | app/src/test 下全部用例，Debug 和 Release 各执行一次，不按固定用例名筛选 |
| Android Lint | Debug 与 Release，保留现有 baseline，新错误阻断 CI |
| Room | KSP 生成 schema，上传 JSON；迁移插桩测试在云端模拟器实际运行 |
| Android 插桩测试 | API 35 x86_64 Google APIs 模拟器运行 connectedDebugAndroidTest，检查报告中确有非跳过用例 |
| APK | 独立 Build APK only 工作流打包、检查签名、上传 APK/R8 mapping，并更新 Preview |
| 正式发布 | 原 Release 工作流手动触发；release_notes_check 仅在提供发布说明时运行 |

Gradle 检查使用 --continue：独立任务尽量继续收集结果，最终仍返回失败。测试报告缺失、损坏、全部跳过、用例失败都不能冒充通过。source、JVM 和模拟器任务独立运行，单个任务失败不阻止其他任务收集报告。

## 报告与下一轮复核

- source-validation：Python 工具测试、源码检查日志。
- android-validation-reports：Gradle 完整输出、Debug/Release 测试 HTML/XML、Lint HTML/XML、Room schema、原 baseline。
- android-device-reports：模拟器测试 HTML/XML、Android 构建日志。
- GitHub Job Summary：JVM 和设备测试计数与失败用例。

失败时仍尝试上传已有报告。runner 安装失败或强制取消发生在报告产生之前时，artifact 可能不存在，应据日志判断，不能称为通过。

推送成功后结束当轮，不等待 CI。下一次开发任务开始，由子代理按上一轮 SHA 查询状态、日志及 artifact；主代理并行做新任务，确认的问题在当前轮修复。

## 本地限制

verify.py 默认 quick，只启动轻量 Python 脚本。logic/full 在非 GitHub Actions 环境直接拒绝；工具单测中的云端路径使用 mock，不启动真实 Gradle/Java。本地不运行 Kotlin 编译、Android 测试、Lint 或 APK 构建。

既有 Windows 中文类路径兼容脚本保留用于工具兼容，不作为执行本地 Gradle 的许可。旧功能修复报告中的本地构建命令是历史记录，不是当前工作流程。

## 不夸大覆盖

baselineprofile 中 coldStart 是性能配置文件生成器，不是功能正确性测试，本次只加入其编译检查，不自动生成或改写生产性能配置。模拟器迁移测试不等同于真机手势、首帧视觉、后台保活、厂商 ROM 或真实模型端点验收。

本次只在本地验证 Python 工具与静态脚本。新增云端设备/Release 测试任务的实际结果留待推送后、下一轮复核。

## 审计

检查 workflows、Gradle runner/Lint 配置、JVM 与 androidTest 测试源、schema、验证脚本、报告工具、README 和历史文档。修复本地 verify 默认启动 Gradle、Lint 不阻断、只上传 baseline、插桩未执行、缺报告被忽略等缺口。没有移除已有功能测试、修改权限/Manifest 或为通过 CI 扩大 Lint baseline。APK 工作流与正式发布入口保留。
