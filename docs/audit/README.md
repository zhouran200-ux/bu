# Lucent个人使用验收：总报告

审查基线：9c3df7b8002c429c487529b8195139c13bfe9c03。只新增审查文档，不修改产品源码、不推送、不发布APK。

## 结论
能使用，不需要推倒重做，也不需要为了1700行或无ViewModel全面重构。但是有应优先处理的数据安全路径、协议错误、配置异步串线和原文坐标问题。长文件最值得优化的是职责边界与失败路径，不是行数本身。

本报告采用[个人验收标准](ACCEPTANCE-STANDARD.md)，模块覆盖见[源码盘点](SOURCE-INVENTORY.md)。完整业务阅读由各模块文件清单证明，盘点不等于全文审查。设备手势、后台回收、耗电和卡顿尚未实测，不能说已全功能验收通过。

## 优先处理：数据与原文可信度
| 优先级 | 问题与触发条件 | 最小建议 | 依据 |
|---|---|---|---|
| P1 | 启动发现数据库非SQLite明文头就删除数据库、WAL及旧备份。旧加密库/异常短头/损坏库都可能被直接清掉 | 先隔离备份并显示恢复失败，不能由读不懂推导出可以永久删除 | [AppDatabase:398–410](../../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L398-L410) |
| P1 | 旧示例识别只检查标题与前16字符；保留开头而改写后部的用户资料仍会在启动清理中被删 | 一次迁移标记+完整指纹；任何改写不得自动永久删 | [LegacyPurge:12–19](../../app/src/main/java/com/lucent/app/data/LegacyPurge.kt#L12-L19)、[TrashCleanup:15–19](../../app/src/main/java/com/lucent/app/data/TrashCleanup.kt#L15-L19) |
| P1（旧设备升级条件） | 25→26/26→27使用DROP COLUMN并吞错误，旧SQLite执行失败会留旧列，Room校验可能阻止升级打开 | 兼容建新表/复制/换表；增加最低支持设备迁移验收，失败不得清库救场 | [迁移:298–317](../../app/src/main/java/com/lucent/app/data/AppDatabase.kt#L298-L317) |
| P1/P2 | 重生成先清内存/删旧答案，之后才检查当前配置；无模型/无密钥时旧回答被错误文案替代 | 配置/权限预检查在前，旧答案保留至新生成成功或明确允许覆盖 | [重生成:472–494](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L472-L494)、[配置检查:318–334](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L318-L334) |
| P2 | 原文切片和阅读块扫描规则不一致；规范化文本反查原文失败仍猜cursor；只取起点高亮整块 | 同一原文扫描保留半开范围，映射失败就拒绝；按切片与块交集高亮 | [SourceTagging](../../app/src/main/java/com/lucent/app/data/SourceTagging.kt)、[范围回推:154–180](../../app/src/main/java/com/lucent/app/ui/ChatScrollMath.kt#L154-L180)、[定位器](../../app/src/main/java/com/lucent/app/ui/SourceLocator.kt) |

已修的SHA-256版本和过期拒跳不重复列为旧缺陷。坐标存在不等于证据正确；模型新增剧情不应贴原著证明。

## 日常使用最值得修的细节
|优先级|问题|建议/证据|
|---|---|---|
|P2|A获取模型时切到B，A响应能在B下显示并应用；连续获取有迟到结果覆盖|请求绑定profile.id+配置快照+requestId，过时结果丢弃；[ApiSettingsPage:805–840](../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt#L805-L840)|
|P2|删当前API之前的非当前项会改选到另一方案|按ID保持身份而不是旧下标；[SettingsScreen:93–101](../../app/src/main/java/com/lucent/app/ui/SettingsScreen.kt#L93-L101)|
|P2|获取中退出API子页，loading保存于父级但子协程取消后没finally清理|请求与loading生命周期一致，finally按请求身份复位；[配置报告](SETTINGS-APP.md)|
|P2|Google part含thought:false时仍把正文归为思考|布尔true才是思考；[LlmClient:633–645](../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L633-L645)|
|P2|HTTP200的SSE error被忽略；没有合法业务终止信息的EOF也当成功|按协议error/finish_reason/message_stop等判断。合法finish_reason不能仅因无DONE判失败；[网络报告](NETWORK-CHAT.md)|
|P2（协议条件）|原生Anthropic非自适应budget模型的预算8192/16384/32768与固定max_tokens8192冲突|预算与正文上限联动并尊重模型能力；AUTO/adaptive不泛化；[请求:489–514](../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L489-L514)|
|P2|SSE多行data逐行JSON导致合法事件被静默丢弃|先按事件组帧再解析；[流处理:597–608](../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L597-L608)|
|P2|字号/行距滑块手指按下即改值，纵向滚动可误改设置|水平touchSlop与方向锁，纵滚不提交；[StepperSlider:137–161](../../app/src/main/java/com/lucent/app/ui/StepperSlider.kt#L137-L161)|
|P2|字体预览倍率被全局Density与局部字号乘两次|预览只应用一次草稿倍率；[Density:217–235](../../app/src/main/java/com/lucent/app/MainActivity.kt#L217-L235)、[预览:189–204](../../app/src/main/java/com/lucent/app/ui/settings/FontSettingsPage.kt#L189-L204)|
|P2|首次对话load暂时失败后ready永久false且缓存旧会话|安全retry/reload，成功清错误；[ChatSession:41–49](../../app/src/main/java/com/lucent/app/chat/ChatSession.kt#L41-L49)|
|P2/P3|模型多选确认先hide，再读可变draft；动画期间可变选择|确认瞬间取快照，关闭期间禁输入；[确认:388](../../app/src/main/java/com/lucent/app/ui/SettingsComponents.kt#L388)|
|P2|API未保存草稿只remember，Activity配置重建丢编辑|按方案保存最小草稿状态；密钥不盲目写持久SavedState；[SettingsScreen:65–71](../../app/src/main/java/com/lucent/app/ui/SettingsScreen.kt#L65-L71)|

其他低优先：空本被显示1来源、长列表排序无自动滚屏、双真主题状态无法关闭、UI触感不尊重系统开关、正式发布旧tag与新APK不一致。分别见[设置](SETTINGS-APP.md)、[视觉](VISUAL-FOUNDATION.md)。这些不应比数据安全和协议错误优先。

## 长文件怎么优化，不为了短而短
[详细拆分建议](LARGE-FILES.md)：
- NotebookChatTab：纯提示词构建、发送/重生成预检查、编辑删除、消息卡、输入栏、设置弹窗；主文件保留页面连接。先锁定状态与回调测试，逐块搬迁。
- ApiSettingsPage：先抽方案身份/请求快照状态，再分编辑表单、获取模型、连接结果与拖动列表；竞态修好再拆布局。
- LlmClient：协议payload纯函数、SSE组帧、三协议事件解析、模型/测试接口。共用现有HttpClient，别引入多层抽象框架。
- AppDatabase：长历史迁移合理，可单独组织，但不能删除旧迁移；启动恢复策略比缩短文件重要。
- Glass、ReorderDrag、Reasoning配置集中有合理性；生成I18n不重构。

拆文件不直接改善帧率。真正性能工作是全文打标签/摘要/引用索引后台计算并按版本缓存、避免每次点击全文扫描、减少长回复重扫描。Markdown parser已经由稳定remember持有，不存在“每chunk新建parser”问题；但安全边界每次全扫和新边界时重解析closed prefix仍有结构成本，无本轮耗时数据。

## 已排除或降低的重要疑点
- 删除撤销窗口内并未落库，undo取消pending是正确逻辑；离开窗口后不能撤销不等于撤销丢数据。
- 编辑提问截断后续历史已有明确文案，是产品选择；建议配置先验/失败恢复，不独立指控“截断本身错误”。
- 删除prepare取消会留内存锁的路径存在，但真实UI用AppScope.io，正常切页不取消；作为故障注入/未来可取消调用路径P2，不当作日常必现P1。
- 字体缓存已有evict与12槽限制，不称无限泄漏。
- 缺所有企业分层、依赖非最新版、没有英文、KTX推荐/primitive state建议均不列为关键问题。

## 标准检查，不等于完整验收
本轮30项Python工具/契约通过；源码卫生111个Kotlin、设置缓存11读11写、版本3.1.2一致；Android lintDebug构建成功，0错误、5警告、1提示，baseline过滤28警告5提示。新告警主要KTX、版本目录、autobox和可更新依赖，对个人app不作为阻断。未运行新全量JVM/设备测试、未打包APK、未做性能测量、未检查本轮云端成功。

## 验收执行顺序
1. 先用测试数据库和备份文件验证启动恢复、误删与最低设备迁移，不在真实个人数据上试。
2. 回环服务器喂三协议错误/终止/false/multiline样本，核对UI与持久化输出。
3. A/B方案慢请求切换、删前项、取消重进、重建编辑；模型确认时连续触摸。
4. TXT/DOC/DOCX长文、重复段落、CRLF、人工标签、引用跨块、旧版本失效；抓主线程与内存指标。
5. 字体滑块纵滚不变、预览与真实文本一致、长列表边缘排序、纯白/动态异常可退出。
6. Android真机锁屏/后台/停止/断网/重启恢复、键盘+模型抽屉、窄屏/大字。必须实际复测才称通过。

## 报告导航
[标准](ACCEPTANCE-STANDARD.md)｜[文件盘点](SOURCE-INVENTORY.md)｜[网络/生成](NETWORK-CHAT.md)｜[聊天UI](CHAT-UI.md)｜[数据/迁移](DATA.md)｜[设置/构建](SETTINGS-APP.md)｜[视觉基础](VISUAL-FOUNDATION.md)｜[长文件](LARGE-FILES.md)｜[反向依赖](REVERSE-DEPENDENCIES.md)｜[AGENTS矩阵](AGENTS-MATRIX.md)｜[开发检查](DEVELOPMENT-CHECKS.md)。