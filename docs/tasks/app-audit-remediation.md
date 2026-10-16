# App 审计整改任务与执行记录

## 范围与交付原则

仅应用源码、应用资源与关联测试；不整理环境、构建产物或无关文档。来源为当前 app 静态审计（94 个主 Kotlin 文件），发现不等于设备复现。
状态：待办 → 进行中 → 已验证 → 已提交；环境不能运行测试时记“待验证”，不得写已完成。每批先补回归测试，再修改，检查残留及依赖，按范围编译/测试；不在本地打包 APK/AAB。推送后不等待本轮 Actions。
职责而非行数验收；共享状态、取消及资源释放不能拆散。允许移动/重命名/新建目录，但每批更新 package、imports、Manifest/R8/资源引用及契约测试；不把结构迁移与行为修复混为一个任务。

## 执行顺序

1. N01–N04：网络协议/敏感信息（先修实际问题）。
2. A01–A03、F01：统一任务/持久化所有权，避免数据串线和覆盖。
3. C01–C04、P01–P03：流式、解析与主线程/会话生命周期。
4. I01–I04、D01–D03：输入资源、存储/缓存/索引风险。
5. R01–R10：在行为测试保护下完成职责重构与目录归类。
6. V01：全项目应用范围残留审计与最终验证。
独立的任务可以同批执行；有依赖的任务不得跳过。所有待办保持可追踪，不以局部提交宣称整体完成。

## 行为修复任务

| ID | 优先级/状态 | 涉及职责与问题 | 验收标准/回归测试 |
|---|---|---|---|
| N01 | P1 已验证（批次01） | network/StreamingResponseReader：逐 data 行解析、拼接不保留换行、坏事件被跳过后成功 | 独立 SSE event reader 在空行聚合多条 data，以换行连接；覆盖 LF/CRLF/CR、注释/心跳、字段、BOM、跨传输分片、空 data、无末尾空行、EOF、异常事件；坏 JSON 不静默成功；取消/部分正文保留；OpenAI/Anthropic/Google 结束条件分别验证 |
| N02 | P1 已验证（批次01） | Gemini thought:false 被 has(thought) 判为思考 | true→reasoning；false/缺省→正文；同事件混合 parts 保序；usage 不丢失。依赖 N01 |
| N03 | P1 待办 | Claude 非自适应预算>=固定 max_tokens | 所有 preset 构成有效预算/输出上限关系；adaptive/auto 和各协议不回归；明确模型上限限制，不凭空设预算 |
| N04 | P1 已验证（批次02） | Google URL key 进入连接报告 | 诊断展示/复制/异常 URL 统一脱敏；覆盖编码/多个查询参数/失败路径；不改变真实鉴权 |
| A01 | P1 待办 | API模型/测试请求缺 profile ID 和请求序号，旧结果污染新方案 | 单任务owner，稳定 profile ID+序号；切方案取消/拒绝旧结果；重复点击乱序、路由离开、失败均测试 |
| A02 | P1 待办 | API子页scope与父loading所有权分离，取消无可靠finally | loading与任务同owner；取消重抛，finally复位；离开/返回无假忙；与 A01 同批 |
| A03 | P1 待办 | SettingsScreen基于profiles旧快照并发全表保存 | 按ID串行mutation/最新事务值；快速增删选排保存不互相覆盖；选中身份稳定；持久化失败回传 |
| F01 | P1 待办 | 字体import快照跨copy再写回，与delete/另一次import交错 | 存储层原子串行索引更新/原子落盘；导入中删字体不会复活，MAX不被绕过；失败清理临时文件；UI锁不是唯一防护 |
| C01 | P1 进行中（批次03） | collectLatest+delay(80)是防抖，连续输出使跟随饥饿 | 持续高频增量仍按界限跟随；手动滚动即退出；结束/键盘resize/历史定位/再跟随不竞争 |
| C02 | P1 待办 | Markdown正文和编号分步发布，行内解析仍主线程 | 原子发布文本+blocks+编号/行内结果；旧任务不能覆盖新文本；编辑/重生成/流结束一致；主题与点击绑定正确 |
| C03 | P2 待办 | 增量解析仍全文找安全边界/重解析闭合前缀 | 真正保留已闭合前缀并只解析必要尾部；fence/空行/格式/脚注回归；长回答benchmark证明收益，不只搬线程 |
| C04 | P2 待办 | 高频阅读位置apply、滚动轨道重建全消息几何 | 合并位置写入并dispose落盘；大消息数几何更新受限；不丢阅读锚点，设备帧耗时验证 |
| P01 | P1 待办 | MainActivity onCreate runBlocking等DataStore；字体组合/query/delete同步IO | 先首帧安全主题/缓存，异步启动读取；provider/字体索引/删除/冷字体预热放IO；失败取消回退；启动闪屏/IME验证 |
| P02 | P2 待办 | ChatSessions永久保留完整闲置会话，scope无释放 | 活跃生成不可随页面退出取消；闲置缓存上限/淘汰/close；删除tombstone生命周期；进出大量笔记本内存验证 |
| P03 | P1 待办 | ChatSession写失败被吞，join不代表成功 | 显式写结果/dirty重试；初始/最终保存失败可见且内容保留；有序写/取消/后台service计数不破坏 |
| I01 | P2 待办 | 文档整文件/ZIP/XML/DOM加载无资源上限 | 输入/条目解压/总解压/文本大小上限；超限友好失败，无部分数据库污染；长文峰值内存测量 |
| I02 | P2 待办 | DOCX XML未显式禁DTD/外实体 | 显式安全配置并验证Android兼容；恶意/合法XML样本；不称默认解析器必可利用 |
| I03 | P2 待办 | CFB链/文件长度/Int偏移计算信任不充分 | overflow-safe bounds、visited、按entry大小停止及分配上限；畸形DOC/环链样本，合法DOC保持 |
| I04 | P2 待办 | 字体复制无大小上限、原地索引writeText | 限流量/大小、原子索引替换及恢复；错误/中断无孤儿；与F01协调 |
| D01 | P2 待办 | SettingsCache部分setter先缓存后edit，组合快照非原子 | durable后更新/明确回退；原子组合snapshot；持久化失败和并发read一致；UserAgent保存成功提示只在成功后 |
| D02 | P2 待办 | NovelSourceIndex在全局锁内全文hash/tag，缓存预算仅body且保留超大单文档 | 锁外计算+身份复核/去重；预算计实际持有副本，单超大文档政策；并发get/evict、改文版本正确 |
| D03 | P2 待办 | 能力registry共享mutableMap无同步；清理查询全读正文/关系；chat查询缺索引 | 明确线程owner/不可变snapshot；按需查询ID/关系；评估conversationId,timestamp索引并保留迁移链；Room迁移/查询计划验证 |
| N05 | P2 待办 | fetchModels/testConnection阻塞Call取消不联动、宽catch | 统一取消->Call.cancel及取消重抛；response.use；超时/取消/错误详情和40秒流读取策略回归 |
| A04 | P2 待办 | 重排1200ms回退不是持久化ack | 以操作版本/保存结果确认；慢写/失败不提前开放破坏性操作；保留同一dragState禁保存/增删选 |

## 职责与目录整理任务

以现有包为兼容基线，小步迁移；以下是目标归属，不强制一次改完目录，也不要求每文件300行。
建议 feature/chat、feature/notebooks、feature/sources、feature/settings/api、feature/settings/font；共享 ui/theme、ui/components、ui/reorder、ui/markdown；network 下 transport/stream/provider；data 下按 persistence/document/font/settings 主题。仅真正共享的组件放共享目录。Android入口与Manifest类名迁移需显式验证。

| ID | 状态/依赖 | 迁移边界与验收 |
|---|---|---|
| R01 | 待办 C01/P03 | NotebookChatTab：Route/Screen、会话配置、发送/编辑/重生成commands、视口controller；DAO/Prompt不留纯Screen；UiChatMessage与事件契约移到chat主题，保持Session唯一生成owner |
| R02 | 待办 A01–A03 | SettingsScreen只route/shell；API draft/方案CRUD/请求统一owner；ApiSettingsPage表单/模型管理/连接结果组件按独立改版理由拆，不用大参数袋假装解耦 |
| R03 | 待办 F01/P01 | FontSettings：系统picker留Route，导入/删除状态协调与字体库/排版预览/命名弹窗分开；字体存储与provider读取分层 |
| R04 | 待办 P01 | MainActivity只Android生命周期/系统栏；Bootstrap、AppTheme、AppShell按职责组织；不复制启动主题策略 |
| R05 | 待办 C02/C03 | Markdown纯AST/增量/行内解析与Compose样式/渲染分离；原文与回答同协议；后台任务/取消与样式身份无竞态 |
| R06 | 待办 | Reorder：几何/状态机、gesture/autoScroll、视觉settle、通用滚动记忆；ApiProfileListSection controller/row可分但同drag owner |
| R07 | 待办 | Glass：色彩方案策略、玻璃交互modifier、palette目录/动画；ChatOverlay思考区与Composer分主题；SettingsComponents通用卡片/导航trail/API picker归属各feature |
| R08 | 待办 I01–I03 | MarkdownDocumentParser：Android Uri适配、格式分发、纯DOCX/编码转换；ReasoningThreeLayer：契约/能力目录/mapper分主题，先定并发owner |
| R09 | 待办 N01–N05 | LlmClient transport、model discovery、connection probe；SSE事件framing与provider decoder分工；LlmClientModels DTO/诊断formatter/CallHandle按主题；保留response/call cancel闭环 |
| R10 | 待办 D03 | Notebooks Route/CRUD use-case/卡片弹窗；Sources import用例/Reader；NotebookPurge跨层session协调上移；AppDatabase migrations与实例装配分离，绝不删历史迁移；Utils仅按主题更名，不硬造Repository层 |
| R11 | 待办 | 每批移动/重命名登记 old→new，更新package/imports/资源/导航/Manifest/R8/测试路径；无重复函数和废弃入口；source契约保留行为断言 |
| Q01 | 待办 | quick现有两处SettingsComponents Color.Gray和ChatMessageComponents 40dp目标警告；评估palette/触摸区域并补视觉/交互验证，不为消警告破坏布局 |
| V01 | 待办 全部 | 全app残留审计；按改动范围JVM/编译/Lint/Python契约；DB迁移与设备测试云端；核对旧行为/阅读位置/拖拽/后台生成/IME/字体/引用；报告未测项和警告。不得“源码测试通过=设备无bug” |

## 不应机械拆分的边界

ChatSession的finally/取消/计数、有序写；SourceTagging算法内部状态；引用协议/版本校验；同feature基础UI集合；单控件StepperSlider；短小AppNavigation/HomeTabs；Entities/DAOs等相关契约。先修行为问题，不因这些文件存在多个紧密相关类型就增加层次。
不保留已撤回误报：“同步onChange后保存必读旧值”；父委托mutableState getter可立即读到新状态，不能凭闭包推测为bug。

## 本轮执行记录

- 批次01：N01/N02 已实现并本地验证；其余任务未完成。新增 SseEventReader 隔离事件边界，单行/单事件8MiB上限；不移动现有provider/package，不混入大目录重排。
- 严格事件语义：事件必须有空行结束，多data用换行连接；未结束的尾事件明确失败，坏JSON/多JSON/服务端error不再静默成功。OpenAI仍需[DONE]，Anthropic仍需message_stop或[DONE]，Google EOF须已有finishReason（或[DONE]），不把空EOF直接当成功。完整事件之前已回调正文由ChatSession原有失败路径保留。
- Gemini true才进reasoning，false/缺省进正文；新增parts顺序与usage覆盖。读取超时40秒/总时限不限制、Call取消与response释放原逻辑不变。
- 测试：SseEventReaderTest覆盖多data、BOM、LF/CRLF/CR、字段/心跳/空data、EOF、大小上限及逐字节UTF-8/CRLF传输；StreamingResponseReaderTest覆盖严格JSON/错误/各provider结束/Gemini正文思考/取消，LlmClientStreamTest覆盖坏事件失败与已交付正文、原网络关闭/取消回归。
- 已通过：定向网络与ChatSession测试；全量Debug JVM测试；Release Kotlin编译；56项Python契约；verify.py quick。最后两个provider测试已单独补跑通过；源码/依赖未变部分复用已通过结果。quick现有警告：两处Color.Gray及40dp点击目标，作为Q01保留，不称零警告。设备/真实中转兼容测试交云端，未本地打包。
- 上轮75c5792已有check与APK runs均success（38949410190/38949410200），日志/报告正文因401/403未能读取。该结果不代表本批新代码已云端通过。
- 结构重构基线为先前75c5792附近的审计状态；文件行号随修复变化，按符号核对。
- 残留审计：StreamingResponseReader旧pendingData/readUtf8Line逐行JSON路径已移除；40秒readTimeout仍在LlmClient；旧Stream测试中“坏事件仍成功”的断言已替换为失败+保留已交付正文。无DB/资源/导航/Manifest/R8变更。提交可由 git log --grep="Fix SSE event framing" 精确定位。
- 发布记录：本批两次 git fetch origin main 遇到 Connection was reset，不能据本地 origin/main 缓存声称已同步远端；仅重试网络，不重跑相同验证。后续记录实际 push 成败。
- 批次02：N04先于N03执行，因为N03涉及模型最大输出能力，需要核对上限，不随意提高max_tokens。新增DiagnosticRedaction，成功/HTTP失败/异常返回统一脱敏，显示/pretty/完整报告再次防御；真实请求鉴权保持不变。覆盖编码参数名、重复敏感参数、URL userinfo、已知密钥及编码值、异常回显和实际请求构造失败。
- 批次02验证：7项DiagnosticRedactionTest、6项TestConnectionDisplayTest及5项原请求payload测试通过；随后全量Debug JVM通过；quick硬约束通过，保留Q01警告。未修改真实URL构造/认证/超时，未打包APK。残留审计：testConnection成功/HTTP失败/异常三个返回路径均redacted；pretty/display/fullReport均执行防御性query脱敏。
- N03仍待办：现有ModelDefinition没有输出上限字段，claude-3-5预算支持识别本身也需要复核；不把“budget+固定余量”作为通用模型能力假设。
- 批次03：C01使用conflate+顺序collect合并最新进度，不取消已开始的80ms等待；等待后再次核对用户跟随/滚动，且等待Compose下一帧再请求定位。虚拟门闩测试覆盖连续增量不饥饿、手动滚动取消待定位、inactive不请求。设备上的IME/结束/历史导航联动仍需验证，不提前把C01写成完整完成。
- Kotlin编译器对默认suspend lambda调用delay触发backend错误，已改为显式注入awaitLayout，不升级依赖/不clean；定向scheduler/scroll/viewport测试、56项Python契约已通过。全量Debug JVM与Release Kotlin编译已通过；随后仅补跑新增停止/离开取消用例。源码未变部分复用结果。
- 不得跳过任务账本直接大规模搬目录。
