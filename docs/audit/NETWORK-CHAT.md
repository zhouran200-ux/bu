# 网络与聊天审查 / App 验收

## 基线、方法与边界

- 审查基线：`9c3df7b8002c429c487529b8195139c13bfe9c03`（开始时项目 HEAD 一致，工作树干净）。仅新增本报告，不修改产品代码。
- 完整逐行读取下列覆盖清单中的网络、chat、作用域、Manifest及相关测试；额外支撑文件注明完整或局部读取。构建产物不是审查源码范围。
- 本轮是静态审查：没有执行测试、连接真实服务商、真机/模拟器验收、性能计时，也不以工作区已有编译产物或测试报告证明本基线通过。
- 标准：结果可预期、失败有反馈、取消及时且不影响其他会话、删除不冻结或复活数据、切页/后台状态有明确边界、恢复可用；不因代码长或命名扣分。
- 优先列出 **6 项源码确认缺陷**。按个人 App 影响与真实旅程分级：本报告六项均列 P2，为局部协议/结果正确性、故障恢复或故障注入下的问题；NC-02/03 不阻断其他协议及全 App，NC-04 虽可冻结整本会话，但现有正常切页不会取消删除作用域。其中协议触发数据、Android 生命周期/磁盘实际故障频率需复测，不把发生概率当作已测结果。

## 逐文件覆盖清单

路径均相对本报告所在目录；行号是上述基线。

| 文件（完整读取） | 行数 | 结论 |
|---|---:|---|
| [ApiModels.kt](<../../app/src/main/java/com/lucent/app/network/ApiModels.kt>) | 3 | 已审，未发现重要问题 |
| [StreamUsage.kt](<../../app/src/main/java/com/lucent/app/network/StreamUsage.kt>) | 51 | 已审，未发现重要问题；未知 usage 用 0 表示，展示层显示“—”，未指控统计造假 |
| [LlmClient.kt](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt>) | 708 | NC-01、02、03、06 |
| [ChatSession.kt](<../../app/src/main/java/com/lucent/app/chat/ChatSession.kt>) | 162 | NC-04、05；取消句柄/写入顺序/最终清 busy 有现有测试覆盖 |
| [ChatSessions.kt](<../../app/src/main/java/com/lucent/app/chat/ChatSessions.kt>) | 78 | NC-04、05 |
| [ChatGenerationService.kt](<../../app/src/main/java/com/lucent/app/chat/ChatGenerationService.kt>) | 133 | 已审，未发现无需额外 Android 触发假设即可确定的重要缺陷；后台风险见后文 |
| [AppScope.kt](<../../app/src/main/java/com/lucent/app/AppScope.kt>) | 16 | 已审，未发现本文件自身重要问题；异常仅日志，调用方是否反馈需按具体操作判断 |
| [AndroidManifest.xml](<../../app/src/main/AndroidManifest.xml>) | 36 | 已审，未发现重要问题；服务不导出，dataSync 类型与权限相符 |
| [ChatSessionTest.kt](<../../app/src/test/java/com/lucent/app/chat/ChatSessionTest.kt>) | 211 | 已审，未发现测试自身重要问题；覆盖切页、停止、并发隔离、串行写、写失败、删除屏障、服务启动失败；未覆盖 NC-04/05 |
| [LlmClientStreamTest.kt](<../../app/src/test/java/com/lucent/app/network/LlmClientStreamTest.kt>) | 308 | 已审，未发现测试自身重要问题；覆盖头部/流等待取消、关闭响应、HTTP 错误、chunked 传输截断；未覆盖正常 HTTP EOF 缺终止事件、SSE error、多行 data 和 Google false |
| [LlmClientFetchModelsTest.kt](<../../app/src/test/java/com/lucent/app/network/LlmClientFetchModelsTest.kt>) | 198 | 已审，未发现重要问题；路径/解析/能力注册覆盖，不覆盖取消和分页 |
| [LlmClientReasoningPayloadTest.kt](<../../app/src/test/java/com/lucent/app/network/LlmClientReasoningPayloadTest.kt>) | 124 | 已审，未发现测试自身重要问题；所有请求实际选 OPENAI，不验证原生 Anthropic 预算约束 |
| [StreamCallHandleTest.kt](<../../app/src/test/java/com/lucent/app/network/StreamCallHandleTest.kt>) | 190 | 已审，未发现重要问题；包括 attach/cancel 真并发及新旧句柄隔离 |
| [StreamUsageParserTest.kt](<../../app/src/test/java/com/lucent/app/network/StreamUsageParserTest.kt>) | 87 | 已审，未发现重要问题 |
| [TestConnectionDisplayTest.kt](<../../app/src/test/java/com/lucent/app/network/TestConnectionDisplayTest.kt>) | 67 | 已审，未发现重要问题 |
| [ChatStatsTest.kt](<../../app/src/test/java/com/lucent/app/ui/ChatStatsTest.kt>) | 42 | 已审，未发现重要问题 |
| [StreamUiSchedulerAndParserTest.kt](<../../app/src/test/java/com/lucent/app/ui/StreamUiSchedulerAndParserTest.kt>) | 136 | 已审，未发现测试自身重要问题；未做长文耗时测量 |
| [ReasoningThreeLayerTest.kt](<../../app/src/test/java/com/lucent/app/data/ReasoningThreeLayerTest.kt>) | 179 | 已审，未发现测试自身重要问题；分层映射测试不是原生服务商协议验收 |
| [NotebookIsolationTest.kt](<../../app/src/test/java/com/lucent/app/data/NotebookIsolationTest.kt>) | 27 | 已审，未发现重要问题 |
| [NotebookPurgeTest.kt](<../../app/src/test/java/com/lucent/app/data/NotebookPurgeTest.kt>) | 65 | 已审，未发现测试自身重要问题；仅纯函数所有权，不覆盖协程取消 |

支撑读取：完整读取 [ReasoningThreeLayer.kt](<../../app/src/main/java/com/lucent/app/data/ReasoningThreeLayer.kt>)（378 行）、[StreamUiScheduler.kt](<../../app/src/main/java/com/lucent/app/ui/StreamUiScheduler.kt>)（87 行）、[NotebookPurge.kt](<../../app/src/main/java/com/lucent/app/data/NotebookPurge.kt>)（51 行）、[应用构建配置](<../../app/build.gradle.kts>)（146 行）；局部读取 [NotebookChatTab.kt:165–194](<../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L165-L194>)、[280–525](<../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L280-L525>)，用于错误反馈/空配置/调用路径，不声称此 UI 文件已完整审查；另局部读取 [NotebooksScreen.kt:112–141](<../../app/src/main/java/com/lucent/app/ui/NotebooksScreen.kt#L112-L141>) 核对真实删除作用域。搜索了单元/设备测试相关标识符，没有发现专门的前台服务/Manifest 生命周期测试。

## 源码确认缺陷

### NC-01 — P2：SSE 内错误或缺少终止事件被判为成功

- **位置**：[LlmClient.kt:595–610](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L595-L610>)、[649–683](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L649-L683>)；下游 [ChatSession.kt:109–113](<../../app/src/main/java/com/lucent/app/chat/ChatSession.kt#L109-L113>)。
- **触发**：HTTP 已是 200 后发送 Anthropic `event: error` / `data: {"type":"error","error":{"message":"overloaded"}}`（或兼容 OpenAI/Google 的 error 对象），随后正常关闭响应；或 HTTP 传输正常结束，但 OpenAI 未发送 [DONE] 且没有任何合法 finish_reason，Anthropic 未发送 message_stop 且没有任何合法业务结束信息。注意区别于不完整 chunked 编码导致 IOException，后者已有测试。
- **后果**：没有识别错误对象；正常 EOF 直接跳出后返回 Result.success。已有文本看起来完整结束，没有网络中断提示；无文本只显示“模型返回了空回复”，隐藏了实际服务端错误。
- **最小建议**：解析三协议 error 事件，返回保留细节的 failure；按协议维护业务终止状态（包含兼容代理合法 finish_reason 等结束信息），没有任何合法业务结束信息就 EOF 应为“不完整流”。不能仅因缺 [DONE] 判失败；Google 以自身终止字段/合法最终响应规则判断，不强加 OpenAI [DONE]。
- **证据级别**：控制流确定；真实网关正常关流形态需实测。补“200+error+合法 HTTP 结束”、“没有任何业务结束信息但 HTTP 正常结束”和“有合法 finish_reason、无 [DONE] 的兼容代理成功结束”的回环测试。

### NC-02 — P2：Google 的 thought:false 正文被错分到思考区

- **位置**：[LlmClient.kt:633–645](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L633-L645>)，尤其 636 行。
- **触发**：parts 中 `{"text":"最终答案","thought":false}`。JSON 明确声明 false，而 has("thought") 为 true。
- **后果**：正文全进入 reasoningText，content 为空；用户可能看到“模型返回了空回复”，真正答案被放进思考区，并按错误字段持久化。
- **最小建议**：只依据布尔值 true 判定 thought；分别验证字段缺失、false、true 与混合 parts，不用字段存在性覆盖 false。
- **证据级别**：源码确认；所选 Google/代理是否显式输出 false 要抓真实报文，不断言所有 Google 请求都会触发。

### NC-03 — P2：原生 Anthropic thinking 预算与 max_tokens 冲突

- **位置**：[LlmClient.kt:489–514](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L489-L514>)；预算来源 [ReasoningThreeLayer.kt:134–145](<../../app/src/main/java/com/lucent/app/data/ReasoningThreeLayer.kt#L134-L145>)。
- **触发**：ANTHROPIC 协议选非自适应 ANTHROPIC_BUDGET 模型；标准/深度/极致分别发预算 8192/16384/32768，却总发 max_tokens=8192。AUTO 使 isAuto=true，整个 thinking/output_config 分支不发送；adaptive/ANTHROPIC_EFFORT 产生 claudeEffort，走 output_config 而不是 budget。因此此结论不泛化到 AUTO、全部 Claude 或全部高档位。即使选实际支持 budget 的模型，预算也应严格小于 max_tokens，当前三个档位不满足。
- **后果**：原生服务商可拒绝请求，较高档位并非产生更深回答；当前预算映射名单中具体模型是否支持 thinking 是另一项需要服务商复核的能力风险，不能靠抬高上限修好不支持 thinking 的模型。
- **最小建议**：先按模型能力验证 thinking；联合计算预算和输出上限，预留正文空间且不超过模型限制；超限给用户清晰反馈。增加 ANTHROPIC 捕获请求测试，而非复用 OPENAI 的 max_completion_tokens 测试。
- **证据级别**：发送数值和内部矛盾源码确认；服务商实际错误码、可用模型上限需要协议/真实接口复测。

### NC-04 — P2：删除准备等待被取消，会永久冻结仍存在的笔记本

- **位置**：[ChatSessions.kt:60–72](<../../app/src/main/java/com/lucent/app/chat/ChatSessions.kt#L60-L72>)、[ChatSession.kt:139–150](<../../app/src/main/java/com/lucent/app/chat/ChatSession.kt#L139-L150>)、[NotebookPurge.kt:31–48](<../../app/src/main/java/com/lucent/app/data/NotebookPurge.kt#L31-L48>)。
- **触发**：闲置会话仍有未完成的旧写入；purge 调用 prepareDelete，先登记 deletedNotebooks、设 unavailable=true/ready=false，再等待 writeTail.join；这段等待期间取消删除协程。
- **后果**：join 抛 CancellationException；purge 的 try/finally 尚未进入，finishDelete 不会运行。笔记本未删但会话不能读写/生成，再次删除被“正在删除或已删除”拒绝，切页也不能恢复；只能重启进程重建内存状态。
- **最小建议**：让 prepareDelete 也处在必达的回滚边界内，记录是否成功取得删除资格，在 NonCancellable 中解除该资格并恢复原状态；避免回滚另一个正在删除的调用。增加“旧写入挂起→开始删除→取消→释放写入→再次读写/删除”的测试。
- **证据级别与实际调用边界**：协程取消路径确定；已局部读取 [NotebooksScreen.kt:119–130](<../../app/src/main/java/com/lucent/app/ui/NotebooksScreen.kt#L119-L130>)，实际删除使用 AppScope.io.launch，而非页面 scope，正常切页不会取消它。因此本项主要是外部取消/故障注入（或未来新增可取消调用方）风险，不宣称当前用户切页会触发，按 P2 而非最先修 P1；进程死亡会清除内存登记，也不等同于本进程永久冻结。不是断言已误删数据库。

### NC-05 — P2：首次读取对话暂时失败后，本进程没有恢复路径

- **位置**：[ChatSession.kt:41–49](<../../app/src/main/java/com/lucent/app/chat/ChatSession.kt#L41-L49>)、[ChatSessions.kt:19–32](<../../app/src/main/java/com/lucent/app/chat/ChatSessions.kt#L19-L32>)，UI [NotebookChatTab.kt:170–175](<../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L170-L175>)。
- **触发**：首次 load() 因暂时数据库/磁盘异常抛错，之后底层恢复正常，用户离开再回来。
- **后果**：ready 始终 false，init 只执行一次且单例 map 返回旧实例；UI 只有错误文案，没有重新读取入口，start 永远返回 false。失败有反馈，但不符合“状态可恢复”。
- **最小建议**：提供安全 reload/retry 路径（成功后清 storageError；失败时不先清空已加载数据），或仅对未 ready 且无活动/写入的实例安全重建。补一次 load 失败、第二次成功的恢复测试。
- **证据级别**：缺恢复控制流源码确认；真实磁盘故障频率未知。

### NC-06 — P2：合法多行 SSE data 被静默丢弃

- **位置**：[LlmClient.kt:597–608](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L597-L608>)。
- **触发**：一个 SSE 事件的 JSON 分成多条 data 行，例如下面报文；SSE 应先按事件合并 data 行再解析 JSON。

```text
data: {"choices":
data: [{"delta":{"content":"hello"}}]}

```

- **后果**：当前每行独立 JSONObject；两行分别解析失败后 continue，正文丢失且不会报告错误。末尾 [DONE] 仍能返回成功的空/不完整文本。
- **最小建议**：按空行分隔事件、拼接 data 行，再解析单个完整 payload；保留 comment/event 字段处理并覆盖 CRLF、拆包、多行、空行和终止事件。
- **证据级别**：合法输入下源码确定；官方通常单行不代表所有兼容代理单行，实际配置是否输出这种事件需实测。

## 已检查但仅列为需真机/故障注入复测的风险

1. **后台生命周期与进程死亡**：[ChatGenerationService.kt:48–88](<../../app/src/main/java/com/lucent/app/chat/ChatGenerationService.kt#L48-L88>) 在通知停止、划掉任务、超时后立即移除前台保护，而会话仍异步 NonCancellable 最终写入；onDestroy 只取消服务观察。是否因 Android/OEM 回收而丢最后一段、或系统另行销毁服务但保留生成，需要真机触发证据，不直接认定一定发生。还要覆盖 targetSdk=36 下 dataSync 时限、拒绝通知、锁屏、省电及系统停止应用。
2. **仅内存中间流/持久化失败**：[ChatSession.kt:95–124](<../../app/src/main/java/com/lucent/app/chat/ChatSession.kt#L95-L124>) 只存开始空槽和最终结果，不定期落盘；异常杀进程恢复可能只有空槽。写异常会被 [52–62](<../../app/src/main/java/com/lucent/app/chat/ChatSession.kt#L52-L62>) 吞掉记 storageError，内存答案保留且 busy 清除，但没有 dirty/retry 队列；空间恢复后旧失败结果不会自动重存。已有 UI Toast，不称“静默失败”；应验收离页、存储恢复、重启后用户是否能明确区分未保存与已保存。
3. **长静默流**：[LlmClient.kt:164–170](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L164-L170>) 有意关闭读取/调用截止时间，相关测试明确锁定此行为。慢思考不应被武断设总超时，但长时间无字节且不报错的坏连接可能一直 busy；验证用户停止与后台超时反馈，不把“无 timeout”本身当缺陷。
4. **模型获取/测试连接取消**：[LlmClient.kt:226–270](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L226-L270>)、[356–405](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt#L356-L405>) 用同步 execute，未像 chatStream 给 Job 注册 call.cancel。离开配置页后是否仍耗网络、旧结果是否覆盖新配置，要核实调用方取消与提交策略；本报告不越过已读范围指控 UI 覆盖。
5. **长文主线程负担**：[ChatSession.kt:96–98](<../../app/src/main/java/com/lucent/app/chat/ChatSession.kt#L96-L98>) 把 append 放在主线程；[StreamUiScheduler.kt:66–82](<../../app/src/main/java/com/lucent/app/ui/StreamUiScheduler.kt#L66-L82>) 多次把完整回答/思考转字符串。网络/JSON 在 IO，不等于 UI 无压力。用大资料+长推理+长正文记录帧时间/内存后再判断是否需要调整；没有任何本轮性能测量值。

## 范围内验收场景清单

| 真实旅程/边界 | 通过条件 | 当前静态结论与验证方式 |
|---|---|---|
| 首启空配置、缺模型/密钥 | 不开网络，给可操作提示，问题仍可编辑重试 | 局部 UI 有显式提示与保存路径；需实际点击验收 |
| 单次正常请求（三协议） | 正文/思考分区正确，统计有值或明确未知，最终写入完成 | usage 常见字段已覆盖；原生协议必须回环+真实接口，重点 NC-02/03 |
| 连续发送/重复生成/两个笔记本同时生成 | 同会话拒重复、跨会话独立，旧句柄不取消新句柄 | 会话与句柄现有测试覆盖这些底层路径；页面交互需验收 |
| 头部未到/思考中/正文中点击停止 | 及时断流，已收到文本保留，busy 清除，再发可用 | 现有回环/会话测试覆盖；需验证通知“停止全部”与真机 |
| HTTP 401/429、200 流内错误、正常 EOF 缺终止、多行 data | 不误报完整成功，保留已收到内容与原因 | 前两类 HTTP 错误已有测试；NC-01/06 为缺陷路径 |
| 切页、切到其他笔记本、回来 | 同一会话继续，状态不串号 | 现有 ChatSession 测试；组合页面/进程恢复仍需验收 |
| Home/锁屏/拒绝通知/划掉任务/系统时限 | 策略可预期；停止时保存已收文本，恢复有明确信息 | 服务与权限静态相符；真机生命周期风险项 1/2 |
| 旧写未完成时删除→取消→再打开/再删 | 不冻结、不复活、不误删 | NC-04；补可控挂起写入取消测试 |
| 初次读取失败→存储恢复→再进入 | 能重载而不要求重启 App | NC-05；补故障一次后成功测试 |
| 写入失败/磁盘满→释放空间→离页/重启 | 显示未保存状态，可重试，不把内存状态当已持久化 | 有 Toast/内存保留测试；重试与恢复是风险项 2 |
| 超长资料、长 reasoning、长正文，生成中滚动 | 取消可操作、滚动不被主线程工作长阻塞、不强制抢阅读位置 | 需要帧时间与真实手势验收；不捏造 ANR/性能数字 |

## 维护性建议（不是实质缺陷，不计入六项）

针对 [LlmClient.kt](<../../app/src/main/java/com/lucent/app/network/LlmClient.kt>) 的具体协议耦合，可按现有函数小步拆分，而非新增企业架构：

- 将 TestConnectionResult 的展示/报告格式和 testConnection 连通性探测放到同一小模块；不让展示文案与流解析混在一个对象。
- 将 fetchModels 的列表解析、路径构建和能力注册作为模型目录模块，明确未来分页与取消入口；保留现有测试接口。
- 按协议抽纯请求构建函数，返回 URL、headers、body；连通性探测和聊天可复用 endpoint 规则，同时分别保持非流/流 payload 差异。这样直接检测 NC-03，避免 Anthropic/Google 的 rawEndpoint 行为与 OpenAI 不一致而无人察觉；是否支持原生 rawEndpoint 应明确定义，不凭分支差异认定产品 bug。
- 将按空行组装 SSE 事件与三协议 payload 解析分开；解析器返回 TextDelta、ReasoningDelta、Usage、Error、End 等少量本地结果，再由网络层处理回调、取消和资源关闭。这是修复 NC-01/02/06 的具体边界，不要求引入额外框架。
- 保留当前 StreamCallHandle 的锁与取消语义，不把“抽文件”当性能优化。统计耗时若调整，应使用单调时钟而非 System.currentTimeMillis；现在系统调时可能改变 answerSeconds/latencyMs，但未做调时验收，不扩大为第七项优先缺陷。

上述收益是协议规则更易单测和少重复，**没有证明拆分能加速、节省内存或提升帧率**。

## 建议验证顺序

先补 NC-01/02/04/05/06 的确定性回环或协程测试，再抓原生 Anthropic 三档预算请求验证 NC-03，最后在 Android 13+ 与 15/16 真机验收后台/通知/存储故障/长文。现有测试的存在只表示意图和覆盖点，本报告未执行，不写“测试通过”。
