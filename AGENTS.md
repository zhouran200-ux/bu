# AGENTS.md

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
8. 执行编译和静态检查。
9. 最后输出残留审计报告。

### 禁止行为：

- 禁止：只删除 UI 就认为功能已经删除。
- 禁止：只搜索一个关键词就认为没有残留。
- 禁止：因为“看起来没用”而删除未经确认的公共代码。
- 禁止：为了让编译通过而保留明显已经废弃的死代码。
- 禁止：在没有确认依赖关系的情况下删除公共类、公共资源或公共依赖。

### 最终标准：

一个功能只有在其代码、UI、数据、状态、事件、导航、资源、配置、依赖、测试、日志和文档均经过检查，并确认不存在无意义残留后，才算完成删除。
