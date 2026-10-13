# 对话生成生命周期修复与残留审计

## 根因与已有工作

原对话页使用 `rememberCoroutineScope` 启动网络生成，消息列表、流式回答 ID 与取消句柄由 Composable 的 `remember` 持有。页面退出组合树时协程取消，重进页面又从数据库读取，无法接回流式状态。

接手时已有未提交的网络取消句柄改动与句柄测试；本次保留并继续完善，没有覆盖其他独立任务。

## 实现

- `chat/ChatSessions.kt`：按笔记本持有进程级会话，仅保存 application context 与 DAO；进入/离开页面不控制任务寿命。
- `chat/ChatSession.kt`：独立监督协程、共享消息与思考状态；每次生成使用新取消句柄；停止同时取消 HTTP 与协程，使用不可取消收尾保存部分回答、思考内容与原时间戳。UNDISPATCHED 启动保证极早停止也进入 finalizer。
- `chat/ChatGenerationService.kt`：dataSync 前台服务保护后台生成，显示运行通知及停止全部入口；结束后自动撤销通知。显式退出、任务移除、系统时限与启动拒绝有独立处理；普通服务销毁不取消新会话，避免旧 stopSelf 与新启动竞态。
- `ui/NotebookChatTab.kt`：只展示、订阅会话状态与触发操作；保留的页面协程仅处理滚动/动画。返回页面使用同一份运行状态。Android 13+ 首次生成请求通知权限，拒绝不取消生成。
- `network/LlmClient.kt`：响应关闭、协程取消传播、阻塞 HTTP 取消桥接、线程安全 sticky 句柄；移除流式 read/call 截止时间，保留 20 秒连接超时，不自动重放请求；Anthropic message_stop 正常终止。
- `data/Daos.kt`：回答使用 Room Upsert。写入顺序统一到会话队列，避免重新生成删除与最终写回乱序；错误、空回复与仅思考内容也保存。
- `data/NotebookPurge.kt`：正在生成的笔记本不能直接删除，需先手动停止；删除期间保留 tombstone，排空已提交写入并拒绝新写入/生成，防止删除后恢复孤立消息。失败时恢复可操作状态。
- `MainActivity.kt`：仅 isFinishing 时请求停止；不在 onStop 或配置重建时取消。

## 验证结果

执行标准命令：

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --console=plain
```

结果：BUILD SUCCESSFUL，21 个测试类、152 项测试，0 失败、0 错误、0 跳过。Debug APK 成功生成。

新增/接续验证包括：

- 9 项会话单测：页面协程销毁后继续生成；返回共享流式状态；首次调度前停止与后续新请求；仅思考输出保留；重复生成拒绝；笔记本隔离；写入排序；存储失败收尾；前台启动失败；删除期间拒绝写回及活动生成不能被删除操作停止。
- 8 项句柄测试：提前停止、重复停止、detach、后续句柄隔离与并发 attach/cancel。
- 13 项本地 HTTP 服务器测试：取消等待响应头/正文的请求；协程取消桥接；DONE 与 Anthropic 结束；异常传播与资源释放；畸形事件兼容；HTTP 错误；截断响应与不重放。

首轮唯一网络测试失败是协程堆栈恢复复制异常导致引用相等断言不成立；已改为验证消息与原异常因果链，保留资源释放断言，最终全量通过。

Lint：0 错误、5 条警告（4 条既有依赖升级提示、1 条 SharedPreferences.edit KTX 风格建议）。既有 baseline 过滤 16 条警告和 5 条提示，另有 12 个旧条目未匹配；未为通过检查修改 baseline。

日志：工作区 `diagnostics/chat-lifecycle-final.log`。单测报告与 APK 分别位于 `app/build/reports/tests/testDebugUnitTest/index.html`、`app/build/outputs/apk/debug/app-debug.apk`。

## 全项目残留审计

已经检查并重复搜索 Kotlin/Java、Activity/Composable、Service/Repository/Manager、导航与 Intent、状态与事件、Room/SharedPreferences、XML/Manifest/资源、Gradle/依赖、ProGuard、后台任务/通知、日志、测试、注释与文档。

- 旧 `streamHandle`、`stoppedByUser` 与页面拥有的生成 launch 已移除，无旧 120 秒流式读取超时残留。
- 唯一业务流式调用路径为 NotebookChatTab → ChatSession → LlmClient；页面 dispose 仅结算待删除消息，不停止生成。
- 剩余页面 scope.launch 都用于 UI；公共 AppScope、StreamUiScheduler 与解析器仍有实际调用，未误删。
- Manifest 增加非导出前台服务与对应权限，无 Worker、启动广播或自动重放请求。
- Room 表结构未变，无迁移或新依赖；没有残留的旧服务/资源需要删除。
- 删除笔记本协调覆盖 UI 可用状态、排队持久化和后台最终保存，独立代码审查发现的竞态已处理。
- git diff 空白检查通过（按项目已有 CRLF 使用 core.whitespace=cr-at-eol）。

## 限制与真机验收

当前 adb 无已连接设备；未安装 APK，未声称完成真机/模拟器或真实模型供应商测试。自动化会话测试验证的是任务独立于页面协程的生命周期，不替代 Android Service 与 Compose 的端到端测试。

建议真机测试：发送长回复 → 左右切换来源/对话 → 返回列表 → 设置 → 另一个笔记本 → Home 切后台 → 返回；应继续同一回复。再验证手动停止、重新发送、配置重建、最近任务划走及正常退出。

不能承诺操作系统和网络层面的“绝不中断”：断网、供应商报错、进程被系统或厂商杀死仍可终止请求。Android 15+ 对 dataSync 前台服务有后台时间预算，达到系统时限会停止并提示；前台服务不是永久保活。取消网络层人为 idle 截止时间意味着无响应但保持连接的服务端需要用户手动停止。未实现自动重发或进程死亡后的续传，也不保证强杀时完成最后一次数据库写入。
