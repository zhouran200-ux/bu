# Lucent 个人使用验收与代码审查报告

审查基线：9c3df7b8002c429c487529b8195139c13bfe9c03。

这是给用户直接阅读的单文件版本。审查按 AGENTS.md 的范围执行，重点是数据安全、失败恢复、配置串线、长文处理、手势冲突和实际可维护性。没有修改产品代码，没有打包 APK。

## 结论

App 当前可以使用，不需要推倒重做，也不需要因为某个文件接近 1700 行就机械拆分。但有几类问题应优先处理：数据库/启动清理可能误删资料；重生成失败可能丢旧答案；API异步结果可能串方案；流式协议错误可能被当成成功；原著引用的范围还不够可靠；字体和长列表交互有确定问题。

## P0：数据安全

### 1. 启动可能删除无法识别的数据库

位置：[AppDatabase.kt:398–410](../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L398-L410)。数据库头不是明文 SQLite format 3 时，代码会删除数据库、WAL、SHM、加密中间文件和锁定文件。损坏、加密迁移中断或历史格式都可能触发。建议先改名隔离并提示恢复，不要直接永久删除。

### 2. 启动清理可能误删用户改过的预置文档

位置：[LegacyPurge.kt:12–19](../app/src/main/java/com/lucent/app/data/LegacyPurge.kt#L12-L19)、[TrashCleanup.kt:15–19](../app/src/main/java/com/lucent/app/data/TrashCleanup.kt#L15-L19)。只检查标题和正文前16字符；用户保留开头、修改后半部分仍可能被启动清理。建议使用一次性安装标识或完整正文指纹，并保留恢复副本。

### 3. 旧设备数据库迁移可能失败

位置：[AppDatabase.kt:298–318](../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L298-L318)。25→26、26→27使用 DROP COLUMN 并吞异常；旧 SQLite 可能留下旧列，随后 Room schema 校验失败。建议建新表复制明确字段，不吞迁移异常，并对最低支持 API 做插桩迁移测试。

## P1/P2：对话、网络与删除

### 4. 重生成可能先丢旧答案

位置：[NotebookChatTab.kt:472–494](../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L472-L494)、[NotebookChatTab.kt:318–334](../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L318-L334)。重生成先清空/删除旧答案，再检查模型配置。配置失效时旧答案无法恢复。建议先校验配置，新回答成功后再替换。

### 5. API 异步请求可能串到另一个方案

位置：[ApiSettingsPage.kt:805–840](../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L805-L840)。方案 A 获取模型或测试期间切到 B，A 的迟到结果可能写进共享状态。建议请求绑定 profile ID、配置快照和 requestId，迟到结果直接丢弃。

### 6. HTTP 200 后的 SSE 错误和多行 data 处理不完整

位置：[LlmClient.kt:595–683](../app/src/main/java/com/lucent/app/network/LlmClient.kt#L595-L683)。HTTP 200 后 error 事件、异常 EOF、多行 data 可能被当作成功或静默丢内容。建议按三家协议处理 error/finish_reason/message_stop，先按 SSE 事件组帧再解析。合法 finish_reason 不能仅因缺少 DONE 判失败。

### 7. Google thought:false 可能被当成思考内容

位置：[LlmClient.kt:633–645](../app/src/main/java/com/lucent/app/network/LlmClient.kt#L633-L645)。代码把字段存在也当作 thought=true，明确 false 时正文可能进入思考区。建议只有布尔值 true 才进入思考分支。

### 8. Anthropic budget 与固定 max_tokens 可能冲突

位置：[LlmClient.kt:489–514](../app/src/main/java/com/lucent/app/network/LlmClient.kt#L489-L514)。部分非自适应 budget 档位可能超过固定 max_tokens。建议按模型能力和协议上限联动计算；AUTO/adaptive 不泛化。

### 9. 首次读取对话失败后没有恢复入口

位置：[ChatSession.kt:41–49](../app/src/main/java/com/lucent/app/chat/ChatSession.kt#L41-L49)。load 失败后 ready 一直为 false，ChatSessions 还缓存失败实例。建议增加安全 retry/reload。

### 10. 删除操作异常时可能半删

位置：[NotebookPurge.kt:31–48](../app/src/main/java/com/lucent/app/data/NotebookPurge.kt#L31-L48)。逐项永久删除后才清理聊天和笔记本，异常可能留下半删状态。建议事务或待删除标记。取消导致内存冻结的路径主要是外部取消/故障注入，不是普通切页必现。

## P2：原文溯源与长文

SourceTagging、ChatScrollMath、SourceLocator 当前存在范围风险：代码围栏规则不统一；名义900字切片可实际到约1800；trim/CRLF/重复文本会让 offset 漂移；人工标签没有复用校验；SourceLocator 只看切片起点，跨块只高亮一个块；每次点击可能重新扫描全文。建议统一扫描器，直接保留原文半开区间，索引按 noteId+revision 缓存，按切片与阅读块交集高亮。

长文发送还会同步打标签并拼接全文，流式时也会不断扫描安全边界和引用编号。没有性能实测，不能说一定卡顿；应使用真实长篇小说采样后再决定是否后台化。

## P2/P3：界面与交互

### 字体滑块误触

位置：[StepperSlider.kt:137–161](../app/src/main/java/com/lucent/app/ui/StepperSlider.kt#L137-L161)。任意按下都改值，没有横向 touch slop/方向锁。纵向滚动设置页可能误改字号、行距或字距。

### 字体预览二次倍率

位置：[MainActivity.kt:217–235](../app/src/main/java/com/lucent/app/MainActivity.kt#L217-L235)、[FontSettingsPage.kt:189–204](../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L189-L204)。全局 Density 已应用保存倍率，预览又乘 activeFontScale，保存非1倍时预览可能不一致。

### 长列表拖拽不能自动滚动

位置：[ReorderDrag.kt:189–200](../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L189-L200)、[ReorderDrag.kt:275–305](../app/src/main/java/com/lucent/app/ui/ReorderDrag.kt#L275-L305)。主要依赖可见项，没有边缘自动滚动；也没有上移/下移等 TalkBack 替代动作。

### 纯白与动态取色异常双真态

位置：[PlatformSettings.kt:50–80](../app/src/main/java/com/lucent/app/ui/PlatformSettings.kt#L50-L80)。正常操作互斥，但异常持久化或快速竞态下两个开关可能都不可用，建议提供始终可用的退出路径。

## 长文件怎么优化

详见 [LARGE-FILES.md](audit/LARGE-FILES.md)。

- NotebookChatTab：提示词、发送/重生成、编辑删除、消息卡、输入栏、设置弹窗分别抽出。先抽状态和持久化动作，保持 session/key 不变。
- ApiSettingsPage：先抽方案身份、草稿和请求快照，再拆模型获取、测试结果和拖动列表。
- LlmClient：拆协议 payload、SSE 组帧、三协议解析和模型接口。
- AppDatabase：迁移可单独组织，但不能删除历史迁移。

拆文件本身不会自动提速；真正的性能优化是减少全文重复扫描、后台构建索引、缓存长文版本。

## 已排除误报

- IncrementalMarkdownParser 由无 key remember 稳定持有，不能说每个 chunk 都重建 parser。
- 删除撤销窗口内尚未落库，undo 可以恢复 pending，不是持久化撤销失效。
- 编辑提问截断后续历史是已告知的产品选择，真正风险是配置失效后的恢复。
- 字体缓存有 evict 和12槽上限，不是无界泄漏。
- 不因为没有 ViewModel、文件很长、依赖不是最新或没有英文切换而扣分。

## 验收顺序

1. 测试数据库损坏/旧格式/旧 SQLite 升级，不在真实数据上试。
2. 回环服务测试三协议 error、终止、thought:false、多行 SSE。
3. 测试 A/B 方案慢请求、API 删除后重生成、编辑失败恢复。
4. 测试长文 CRLF、代码围栏、人工标签、跨块引用。
5. 真机测试字体滑块、模型抽屉、IME、长列表、后台/锁屏/进程回收。

## 详细报告索引

- [数据与数据库](audit/DATA.md)
- [网络与生成](audit/NETWORK-CHAT.md)
- [聊天 UI](audit/CHAT-UI.md)
- [设置与构建](audit/SETTINGS-APP.md)
- [视觉与主题](audit/VISUAL-FOUNDATION.md)
- [反向依赖](audit/REVERSE-DEPENDENCIES.md)
- [AGENTS 范围矩阵](audit/AGENTS-MATRIX.md)
- [开发检查工具](audit/DEVELOPMENT-CHECKS.md)
- [长文件边界](audit/LARGE-FILES.md)
- [个人验收标准](audit/ACCEPTANCE-STANDARD.md)
