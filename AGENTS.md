# AGENTS.md

## 任务交付约定

- 不在本地启动 OpenJDK/Java、Gradle、Kotlin 编译、Android 单元测试、Lint 或 APK 打包。编译、Android 测试、Lint 和 APK 构建全部交给 GitHub Actions。
- 本轮流程：修改代码、补充测试用例、运行不依赖 Java/Gradle 的轻量测试或文本级检查，然后提交并推送到用户的 GitHub 仓库。
- 推送成功后即结束本轮任务；不等待、轮询或持续跟踪本轮 GitHub Actions 的结果，不把“已推送”说成“云端检查通过”。
- 下一次实际开发任务开始时，先派出一个独立子代理，检查上一轮提交对应的 GitHub Actions 结果、日志和测试报告；主代理同时推进当前任务，不串行等待该检查。
- 子代理发现需要修复的 bug 或有依据的优化项时，在当前轮处理并验证；仅有检查工作时不要为等待云端结果反复启动任务。若上轮流程仍未结束，如实记录，不持续轮询。
- 收尾记录提交 SHA 和仓库/Actions 入口，供下一轮子代理精确对应提交。
- 推送前检查远端与工作流触发条件，不强制推送、不覆盖远端改动；若分支存在冲突或目标不明确，先确认。
- 当前仓库 `zhouran200-ux/bu` 的 `main` 推送会触发 `check` 和 `Build APK only`，后者更新 Preview Release；正式 Release 工作流按需手动触发，不擅自发布正式版本。

## 最高优先级规范：功能变更后的强制清理规则

任何功能被删除、替换、重构或迁移后，不能只修改当前功能的主要代码。

必须进行一次全项目级别的残留审计。

### 审计范围至少包括：

- Kotlin / Java 源代码
- Activity / Fragment / Composable / ViewModel
- Repository / DataSource / Service / Manager
- Navigation / Deep Link / Intent
- State / Flow / LiveData / Event / Callback
- Room / SQLite / DataStore / SharedPreferences
- XML / Drawable / Vector / String / Color / Style / Theme
- Manifest
- Gradle / Dependencies
- ProGuard / R8
- Notification / Worker / Background Task
- Analytics / Logging / Feature Flag
- Unit Test / UI Test / Mock / Fake
- Comment / TODO / FIXME / Documentation
- Unused import / dead code

### 执行顺序必须为：

1. 识别旧功能的完整依赖关系。
2. 全局搜索旧功能的所有标识符。
3. 区分“旧功能专属代码”和“公共代码”。
4. 删除旧功能专属代码。
5. 删除失效的引用、资源、配置和依赖。
6. 再次进行全局搜索。
7. 修复因为删除产生的编译或引用问题。
8. 本地仅执行不依赖 Java/Gradle 的轻量检查；编译、Android 测试与 Lint 由推送后的 GitHub Actions 执行，下一轮由子代理复核结果。
9. 最后输出残留审计报告。

### 禁止行为：

- 禁止：只删除 UI 就认为功能已经删除。
- 禁止：只搜索一个关键词就认为没有残留。
- 禁止：因为“看起来没用”而删除未经确认的公共代码。
- 禁止：为了让编译通过而保留明显已经废弃的死代码。
- 禁止：在没有确认依赖关系的情况下删除公共类、公共资源或公共依赖。

### 最终标准：

一个功能只有在其代码、UI、数据、状态、事件、导航、资源、配置、依赖、测试、日志和文档均经过检查，并确认不存在无意义残留后，才算完成删除。
