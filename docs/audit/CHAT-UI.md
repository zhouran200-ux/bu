# CHAT UI 审查报告

- 基线：9c3df7b8002c429c487529b8195139c13bfe9c03
- 范围：指定 UI 文件、直接调用者与相关 tests；未改产品代码。
- 口径：源码确认缺陷可由代码路径直接推导；需真机复测风险不定性为已发生 bug。

## 优先问题

### 已复核：删除撤销不是已确认缺陷
- [NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L522-L549) 的 pendingDelete 先只从内存移除；undoDelete 读取当前 pendingDelete、插回列表并清空 pending，因此在 10 秒撤销窗口内并未执行 DAO 删除。
- [NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L302-L310) 只有 settlePendingDelete（超时、关闭 snackbar 或 dispose）才永久删除；dispose 后撤销入口也随组件消失，不能把“没有跨页面撤销”误报成数据恢复 bug。建议补行为测试验证时序，但本轮不列问题。

### P2｜源码确认的长文流式性能结构风险：安全边界与引用编号每个 chunk 都重扫全文
- [Markdown.kt](../../app/src/main/java/com/lucent/app/ui/Markdown.kt#L170-L182) 每次追加文本都调用 `findSafeClosedSplit(text)`；该函数在 [Markdown.kt](../../app/src/main/java/com/lucent/app/ui/Markdown.kt#L192-L215) 对全文 `text.lines()` 并逐行扫描。即便 parser 实例稳定，扫描仍随全文长度重复发生。
- 当安全边界推进时，[Markdown.kt](../../app/src/main/java/com/lucent/app/ui/Markdown.kt#L171-L175) 会对从头到新边界的完整 closed prefix 再次 `parseBlocks`，并非只解析新增段；[Markdown.kt](../../app/src/main/java/com/lucent/app/ui/Markdown.kt#L177-L182) 还会重建 tail block 列表。
- [Markdown.kt](../../app/src/main/java/com/lucent/app/ui/Markdown.kt#L322-L325) 每次文本变化还以全文调用 [SourceCitations.numbering](../../app/src/main/java/com/lucent/app/data/SourceCitations.kt#L28-L34)，再次扫描所有引用标记。源码结构显示长文/高频 chunk 风险，但本轮没有掉帧实测。

### P2｜源码确认的来源定位性能/偏移风险：点击引用会重新给全文做 tagging，偏移依赖文本换行形态
- [SourceLocator.kt](../../app/src/main/java/com/lucent/app/ui/SourceLocator.kt#L16-L19) 每次引用定位都调用 `SourceTagging.tag(title, body)`；[SourceTagging.kt](../../app/src/main/java/com/lucent/app/data/SourceTagging.kt#L32-L55) 重新扫描并重建章节、切片和输出，长文点击引用不是复用已生成索引。
- [SourceTagging.kt](../../app/src/main/java/com/lucent/app/data/SourceTagging.kt#L67-L73) 通过 `body.split("\n")` 和每行 `+ 1` 推算 offset；若资料保留 CRLF 或其他换行表示，推算的字符位置可能偏移。现有测试覆盖常规 LF 和定位结果，但未覆盖 CRLF/超长重复段；这是需真机/数据样本复测的偏移风险，不宣称必现错位。
- [NotebookSourcesTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookSourcesTab.kt#L181-L203) 已对 blocks 做 remember，但 focus 触发时仍通过 SourceLocator 重建 tagging；建议把导入时的索引/offset 与 blocks 共用，或至少缓存 `Tagged`，并补 CRLF、重复正文和长文定位测试。

### 合并的状态风险建议（不将产品分支截断本身列为 bug）
- 编辑提问的截断行为已由界面文案明确告知“会删掉这条之后的对话”（[NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L850-L860)），属于已确认的产品选择，不单独计为删除缺陷。
- 仍建议把新请求的配置校验放在任何破坏性截断/替换之前，并考虑失败时保留恢复入口：[NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L497-L511)、[ChatSession.kt](../../app/src/main/java/com/lucent/app/chat/ChatSession.kt#L105-L124)。该项与下方重生成配置失效问题同属“先验证、后破坏”的状态健壮性建议。

### P1｜源码确认：重生成先删除旧答案，配置无效时直接丢失原答案
- [NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L472-L494) regenerateAnswer 先将原回答清空并从 DAO 删除，然后才调用 askModel。
- [NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L316-L335) askModel 在模型为空时直接把槽位写成“未配置可用模型”，但配置解析/缺失路径可能在前置流程直接 return；因此用户删除 API/模型后点“重新生成”，原答案已被删除，无法恢复，且新答案不一定启动。应先校验可用 profile/model，再原子替换或保留旧答案直到新生成成功，并补缺配置重生成测试。

### P2/P3｜源码确认竞态：模型抽屉确认提交的是 hide 完成时的可变 draft
- [ModelPickerDrawer.kt](../../app/src/main/java/com/lucent/app/ui/ModelPickerDrawer.kt#L48-L62) close 先 hide，完成后才 afterHide；[SettingsComponents.kt](../../app/src/main/java/com/lucent/app/ui/SettingsComponents.kt#L387-L404) 确认 lambda 捕获 draft。
- [SettingsComponents.kt](../../app/src/main/java/com/lucent/app/ui/SettingsComponents.kt#L221-L223) draft 可继续变化。确认后若动画期间仍能点击列表，onDone 读取的不是点击确定瞬间快照；连续确认也由 closing 拦截但没有组件测试。建议确认时复制 draft 快照并关闭交互，补确认/取消/连续点击测试。

## 需真机复测风险

1. **横杠是否只动抽屉**：静态上 [ModelPickerDrawer.kt](../../app/src/main/java/com/lucent/app/ui/ModelPickerDrawer.kt#L64-L83) 只给 ModalBottomSheet modifier 加 translationY，且 [ProfileModelPickerSheet.kt](../../app/src/main/java/com/lucent/app/ui/ProfileModelPickerSheet.kt#L70-L76) 关闭 Material 内建手势；需设备验证 scrim、Material 锚点/inset、快速反向拖动不会带动底层聊天或错位。
2. **内容滚动与手势**：模型列表独立 LazyColumn（[ProfileModelPickerSheet.kt](../../app/src/main/java/com/lucent/app/ui/ProfileModelPickerSheet.kt#L79-L82)）；历史列表独立 LazyColumn（[ChatHistorySheet.kt](../../app/src/main/java/com/lucent/app/ui/ChatHistorySheet.kt#L162-L185)），跳转由 [NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L782-L808) 回调；长列表需验证滚动、sheet 下拉、点击后不误关。
3. **Rail pointer 边界**：收起 rail 首次触摸只展开，展开后才长按拖拽（[ChatScrollRail.kt](../../app/src/main/java/com/lucent/app/ui/ChatScrollRail.kt#L119-L153)）；纯数学测试不覆盖 pointer input。需复测短点、thumb 长按、轨道长按、快速滑动、IME 隐藏。
4. **变量高度定位**：[ChatScrollRail.kt](../../app/src/main/java/com/lucent/app/ui/ChatScrollRail.kt#L32-L52) 用可见 item 平均高度，[ChatScrollMath.kt](../../app/src/main/java/com/lucent/app/ui/ChatScrollMath.kt#L42-L58) 估算目标；测试主要是均匀高度（[ChatScrollMathTest.kt](../../app/src/test/java/com/lucent/app/ui/ChatScrollMathTest.kt#L29-L80)），长 Markdown、展开思考 lane、流式高度需设备复测。
5. **IME/弹层恢复**：[PopupIme.kt](../../app/src/main/java/com/lucent/app/ui/PopupIme.kt#L27-L51) 使用 ALT_FOCUSABLE_IM 与重新请求焦点；需复测键盘开着打开模型/设置、返回、切后台恢复，不把输入法差异静态定性。

## 逐文件结论

| 文件 | 结论 |
|---|---|
| [NotebookChatTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt) | 删除撤销时序已复核未发现重要缺陷；编辑截断为已告知的产品选择，另有配置校验顺序建议与重生成风险；发送/停止/失败反馈路径已审。 |
| [Markdown.kt](../../app/src/main/java/com/lucent/app/ui/Markdown.kt) | parser 生命周期稳定；发现长文流式时全文安全边界扫描、closed prefix 重解析与引用编号重扫的结构性性能风险，未编造实测。 |
| [ModelPickerDrawer.kt](../../app/src/main/java/com/lucent/app/ui/ModelPickerDrawer.kt) | 横杠 modifier 静态上只作用 sheet；确认 draft 快照/Material 手势需修测。 |
| [ProfileModelPickerSheet.kt](../../app/src/main/java/com/lucent/app/ui/ProfileModelPickerSheet.kt) | 已审；独立列表滚动合理，未发现其他已证实重要 bug。 |
| [ModelPickerPosition.kt](../../app/src/main/java/com/lucent/app/ui/ModelPickerPosition.kt) | 已审；索引映射与测试一致，未发现重要问题。 |
| [ChatHistorySheet.kt](../../app/src/main/java/com/lucent/app/ui/ChatHistorySheet.kt) | 已审；独立内容滚动合理，长历史手势列复测风险。 |
| [ChatScrollMath.kt](../../app/src/main/java/com/lucent/app/ui/ChatScrollMath.kt) | 已审；边界数学测试充分，变量高度为风险非确认 bug。 |
| [ChatScrollRail.kt](../../app/src/main/java/com/lucent/app/ui/ChatScrollRail.kt) | 已审；pointer input 缺设备覆盖，未静态定性。 |
| [StreamUiScheduler.kt](../../app/src/main/java/com/lucent/app/ui/StreamUiScheduler.kt) | 已审；flush/取消由 [ChatSession.kt](../../app/src/main/java/com/lucent/app/chat/ChatSession.kt#L101-L130) 收尾覆盖，未发现重要逻辑 bug。 |
| [NotebookSourcesTab.kt](../../app/src/main/java/com/lucent/app/ui/NotebookSourcesTab.kt) | 已审；导入走 AppScope.io；定位依赖 SourceLocator 的全文重建与换行偏移假设，详见 P2 风险。 |
| [SourceLocator.kt](../../app/src/main/java/com/lucent/app/ui/SourceLocator.kt) | 发现引用定位每次全文重建 tagging；CRLF/重复正文偏移列为数据样本复测风险。 |
| [ReadingPositionMemory.kt](../../app/src/main/java/com/lucent/app/ui/ReadingPositionMemory.kt) | 已审；稳定 key、缺锚点、空文档、损坏持久化有测试，未发现重要问题。 |
| [PopupIme.kt](../../app/src/main/java/com/lucent/app/ui/PopupIme.kt) | 已审；flag 恢复明确，不同设备/输入法需复测。 |

## 验收场景清单

- 首次启动：无历史、无来源、无 profile；空态、发送失败反馈。
- 模型选择：空/单/多 profile；展开、列表滚动、短拖/长拖、连续确认、取消、返回；确认一次、取消不提交 draft。
- 对话：短问、长回答、连续发送、流式停止、网络失败、空回复、后台/切页返回；验证输入 draft、模型、阅读位置恢复。
- 删除/编辑：删除撤销、撤销前切页/后台、编辑提问、重新生成失败；验证数据库与 UI 一致、无静默误删。
- 来源：空来源、txt/doc/docx、长文滚动、引用跳转、未知引用、Chat/Sources 切换、后台恢复；验证导入不阻塞主线程。
- Material：横杠只动 sheet，底层聊天不动；sheet 内容可滚动；IME、scrim、返回、旋转/恢复不抢手势。

## 最小风险后续步骤

1. 先补删除撤销时序测试（撤销窗口内不删、超时/dispose 后删除），避免回归；不改变当前正确语义。
2. 把模型确认 draft 在点击时快照，关闭期间禁用列表输入，并补组件状态测试；这是低风险交互修复。
3. 针对 Markdown 长回答做真机 profiling；parser 生命周期无需改，但应优先验证全文安全边界扫描、closed prefix 重解析和引用编号重扫，再决定增量索引或移出组合。
4. 对 1797 行 NotebookChatTab 做维护性拆分：先抽纯状态/持久化动作（约 L302-L520）、删除/编辑流程（约 L472-L549）、消息列表与滚动 rail（约 L608-L808）、消息卡/思考 lane（约 L880-L1373）、composer 与 settings dialogs（约 L1375-L1790）；每步保持 key、session 状态和回调不变，再跑对应 tests。最后做 Material/IME/rail 真机矩阵。

更正记录：此前误将 `remember(text)` 误读为重建 IncrementalMarkdownParser；实际 parser 由无 key 的 `remember { ... }` 稳定持有，已撤销该性能缺陷。长文成本仅建议真机测量，不编造性能数据；职责拆分建议仅作维护性建议。
