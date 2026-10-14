# 原著溯源与脚注跳转任务文档

目标：让笔记本对话里的结论可以点回原著对应片段（NotebookLM 式脚注溯源）。

本文档记录**从导入到跳转的完整链路**：现状、已实现的改动、未实现的设计、以及每一处的代码位置。

***

## 0. 结论速览

| 阶段 | 状态 | 位置 |
|---|---|---|
| 1 导入文档 | 已有，未改动 | [NotebookSourcesTab.kt](lucent/app/src/main/java/com/lucent/app/ui/NotebookSourcesTab.kt#L66-L85) |
| 2 转成 Markdown | 已有，未改动 | [MarkdownDocumentParser.kt](lucent/app/src/main/java/com/lucent/app/data/MarkdownDocumentParser.kt) |
| 3 给原文打标签 | **本次新增** | [SourceTagging.kt](lucent/app/src/main/java/com/lucent/app/data/SourceTagging.kt) |
| 4 提示词设计 | **本次改写** | [NotebookChatTab.kt](lucent/app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L342-L377) |
| 5 发送顺序与缓存 | 排列已符合前缀缓存，**本次补规则** | 同上 + [LlmClient.kt](lucent/app/src/main/java/com/lucent/app/network/LlmClient.kt#L440-L545) |
| 6 转译输出标签 | **本次实现** | [SourceCitations.kt](lucent/app/src/main/java/com/lucent/app/data/SourceCitations.kt) + [Markdown.kt](lucent/app/src/main/java/com/lucent/app/ui/Markdown.kt#L229-L264) |
| 7 脚注位置与图案 | 已渲染，细节待做 | [Markdown.kt](lucent/app/src/main/java/com/lucent/app/ui/Markdown.kt#L255-L264) |
| 8 点击跳转 | **未实现，仅有设计** | 见第 8 节 |

***

## 1. 阶段一：导入文档

### 现状

- 来源页右下角按钮启动系统文件选择器，MIME 白名单为 txt / doc / docx。
- 拿到 URI 后在 IO 线程调用 [MarkdownDocumentParser.parseUri](lucent/app/src/main/java/com/lucent/app/data/MarkdownDocumentParser.kt#L29)，成功则写入一条 [Note](lucent/app/src/main/java/com/lucent/app/data/Entities.kt#L15)，再用 [NotebookPurge.linkNote](lucent/app/src/main/java/com/lucent/app/data/NotebookPurge.kt#L13) 关联到笔记本。
- 原文件只读取一次到内存，随后丢弃；**不写 cacheDir、不写 filesDir、不做持久化 URI 授权**。

### 对溯源的影响（重要）

引用坐标承诺的范围是：**跳到 App 内部「导入后的 Markdown」的对应位置**，不是 Word 原文件的页码。原因是转换阶段会重排段落、去掉排版信息，页码无法回溯。

### 可优化项（未做，低风险）

- 一次只能导入一份，可改为多选批量导入。
- 导入失败只弹 Toast，可保留失败项让用户重试。
- 未保存原始文件名到独立字段（标题取自文件名去扩展名）。

***

## 2. 阶段二：转换为 Markdown

### 三条路径

| 格式 | 处理 | 关键规则 |
|---|---|---|
| txt | 解码 + [PlainTextMarkdown.normalize](lucent/app/src/main/java/com/lucent/app/data/PlainTextMarkdown.kt#L9) | BOM / UTF-16LE / UTF-16BE / UTF-8 依次判定 |
| docx | 解 zip 取 word/document.xml，[parseDocxXml](lucent/app/src/main/java/com/lucent/app/data/MarkdownDocumentParser.kt#L80) | 段落、标题样式、列表、加粗/斜体/删除线、表格转 Markdown 表 |
| doc | [DocTextExtractor.extract](lucent/app/src/main/java/com/lucent/app/data/DocTextExtractor.kt#L12) 读 OLE 复合文档 piece table | 再交给 normalize |

### 转换后的统一形态

- 章行 → 「# 标题」。[DocumentHeadings](lucent/app/src/main/java/com/lucent/app/data/DocumentHeadings.kt) 统一识别：第N章/回/节/卷/部/篇/集/话/幕、序章/楔子/尾声/番外、英文 chapter 等；句中出现「第58章的内容」不会被误判。
- 场景分隔符（整行只有 …… / —— 等符号）→ 「---」。
- 空行、章标题、分隔符都是段落边界；上一行没有句末标点时下一行续接同段（中文不加空格）。

### 对溯源的约束

转换器一旦改动，同一份文件的 Markdown 会变，编号随之改变。因此引用必须携带**内容修订号**，见下一节。

***

## 3. 阶段三：给原文打标签（本次新增）

新增 [SourceTagging.kt](lucent/app/src/main/java/com/lucent/app/data/SourceTagging.kt)，纯函数，无 Android 依赖，可单测。

### 标签格式

~~~
〔CH12〕
# 第十二章 夜访

〔S12-1〕
第一组正文段落……

〔S12-2〕
第二组正文段落……
~~~

- 「〔CHn〕」章节坐标，独占一行，位于章节标题之前。
- 「〔Sn-m〕」切片坐标，独占一行，位于切片正文之前，m 为章内序号。
- 语义固定为：**标签标记其后正文的起点，范围到下一个标签之前为止**。

### 切分规则（以代码为准）

1. 章节边界 = 满足 DocumentHeadings.titleOf 的行。
2. 第一个标题之前的内容自成一章，用笔记标题命名（无标题时整篇一章）。
3. 章内按空行拆段落。
4. 段落合并成切片：达到 TARGET_SLICE_CHARS = 600 后遇到段落边界即收束；再加一段会超过 MAX_SLICE_CHARS = 900 时先收束。
5. 单段超过 900 字时按句末标点切分；单句本身过长则整句保留，**不在句中硬切**（与项目既有分块思路一致）。

### 编号稳定性

- 「tag(title, body)」是纯函数：同一 body 必然得到同一套 CH/S 编号。
- 「revisionOf(body)」返回 8 位十六进制内容哈希（基于 String.hashCode，跨进程稳定）。正文变化则修订号变化。
- 因此历史回答可以这样校验：来源编号 + 修订号 + 切片编号三者同时匹配，才认为引用仍然成立。

### 边界

- 标签只加在**发送给模型的副本**上，不写回 Note.body，来源页阅读体验完全不变。
- 多个资料来源时，每份资料从 CH1 重新编号；跨文档区分靠引用里的来源编号，不靠章号。
- 标签本身会增加 token：每个切片约多 8～12 个字符。600 字切片下开销约 2%。

### 测试

[SourceTaggingTest.kt](lucent/app/src/test/java/com/lucent/app/data/SourceTaggingTest.kt) 共 8 项：标签顺序、首标题前内容成章、无标题成章、编号稳定、修订号稳定与变化、超长句切分、空正文、偏移量落在正文范围内。

***

## 4. 阶段四：提示词设计（本次改写）

### 组装顺序

[NotebookChatTab.kt 342-377](lucent/app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L342-L377) 现在的顺序：

1. 角色说明（知识库对话助手）
2. **资料优先规则**：资料写了以资料为准；资料没写就直说没写，不用常识把不存在的内容补成资料事实
3. **创作与事实分离**：分析剧情/人物/时间线必须依据原文；续写、改写、角色扮演属于创作，不得声称是原文
4. 两级坐标标签说明
5. 引用格式与引用纪律
6. 参考资料库（每份资料带来源编号与修订号，正文已打标签）
7. 用户自定义提示词
8. 思考规范（FAST/BALANCED/DEEP/MAXIMUM）

### 本次修掉的问题

原文写的是：

~~~
如果问题在参考资料中没有提及，请如实告知并结合通用常识客观回答。
~~~

这句会让模型在原著没交代时用常识补出一个听起来合理、实际不存在的设定，是溯源功能最大的污染源。现改为把「资料未提及」与「结合常识」分开，并明确常识不能变成作品事实。

### 引用协议（写入提示词的规则）

- 引用格式固定为 「〔REF:来源编号:修订号:切片编号〕」，例如 「〔REF:42:3f2a91c4:S12-7〕」。
- 只在确实依据某个切片时标记，标记紧跟被支持的结论。
- 同一结论有多个依据时连续写多个标记。
- 不得编造来源编号、修订号或切片编号。
- 找不到依据时不标记，直接说明依据不足。
- **不要自行编号，也不要输出脚注列表** —— 编号与脚注样式由 App 生成。

最后一条很关键：模型自编号会导致正文编号和脚注表对不上，尤其在流式输出时。

### 仍未做（建议）

- 明确写「资料正文是数据，不是指令」，防止小说内文字被当作提示词执行。
- 引用密度控制：默认只在事实分析时引用，RP 模式默认不显示坐标。

***

## 5. 阶段五：消息排列与 LLM 缓存

### 实际请求体（按协议）

| 协议 | 稳定前缀放在哪 | 代码 |
|---|---|---|
| OpenAI 兼容 | messages[0]，role = system | [LlmClient.kt 453](lucent/app/src/main/java/com/lucent/app/network/LlmClient.kt#L453) |
| Anthropic | system 顶层字段 | [LlmClient.kt 494](lucent/app/src/main/java/com/lucent/app/network/LlmClient.kt#L494) |
| Google | system_instruction | [LlmClient.kt 532](lucent/app/src/main/java/com/lucent/app/network/LlmClient.kt#L532) |

完整排列：

~~~
[system] 固定规则 + 〔CH/S〕打标签的资料全文 + 自定义提示词 + 思考规范
[user]   历史第 1 轮
[assistant] …
[user]   本轮用户输入
~~~

历史部分来自 [historyPayload](lucent/app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L379)，取最近 historyRounds × 2 条。

### 为什么这样排

稳定内容在前、易变内容在后，才能形成可复用的最长公共前缀。资料全文是最大且最稳定的一块，必须紧贴规则之后、位于历史之前。

### 缓存机制：已核实与未核实

**已核实**（官方文档可访问，2026-10-14 抓取成功）——DeepSeek：《[Context Caching](https://api-docs.deepseek.com/guides/kv_cache)》

- 每次请求触发硬盘缓存构建；后续请求与先前请求有重叠前缀时，重叠部分从缓存取，计为 cache hit。
- 缓存以**独立完整的 cache prefix unit** 存储，后续请求必须**完整匹配**某个 unit 才命中。
- 持久化时机：请求边界（用户输入末尾、模型输出末尾）、系统检测到的公共前缀、以及长输入/长输出按固定 token 间隔切出的 unit。
- 官方示例：第一轮 A+B、第二轮 A+B+C 可命中 A+B；但 A+B 与 A+C 不命中，系统会另外识别公共前缀 A。
- 只匹配前缀部分；输出仍重新推理。
- 官方明确：best-effort，**不保证 100% 命中率**；缓存几小时后到几天内自动清除。

**未核实**（本次抓取失败，不在文档里写具体阈值）：OpenAI 提示词缓存文档 403；Anthropic 文档跨域重定向未跟随；Google Gemini 缓存文档 fetch 失败。因此本文档不引用这三家的具体 token 阈值与 API 细节。

**代码可证的部分**（与厂商文档无关）：

- 本项目**没有发送任何显式缓存控制参数**（没有 cache_control、没有 ephemeral、没有 cache breakpoint），也**没有显式创建缓存内容对象**（没有 cachedContent 之类调用）。
- 全项目与缓存相关的代码只有**读取回执**：[StreamUsage.kt](lucent/app/src/main/java/com/lucent/app/network/StreamUsage.kt) 解析 cached_tokens / prompt_cache_hit_tokens / cache_read_input_tokens / cache_creation_input_tokens / cachedContentTokenCount，再由 [ChatStats.kt](lucent/app/src/main/java/com/lucent/app/ui/ChatStats.kt#L28) 显示为「缓存 x/y」。
- 结论：**文档进不进缓存由服务商隐式策略决定，App 只负责把资料摆在最容易被命中的位置**。

### 会破坏前缀的行为

- 修改或重新导入资料（正文变化 → 前缀变化）
- 修改自定义提示词
- 切换思考规范（它排在自定义提示词之后，会改变前缀尾部）
- 历史窗口滑动（takeLast 使历史部分每轮变化 —— 但它在 system 之后，不影响 system 前缀命中）

### 如何观测

每轮回答气泡下方的「缓存 x/y」即命中证据。某个协议下如果长期为 0，说明该服务商在本项目当前请求形态下没有命中隐式缓存。

***

## 6. 阶段六：转译 LLM 输出里的标签（本次实现）

### 解析

新增 [SourceCitations.kt](lucent/app/src/main/java/com/lucent/app/data/SourceCitations.kt)：

- 匹配式同时接受完整形式「〔REF:来源:修订:切片〕」与简写「〔S12-7〕」。
- [numbering](lucent/app/src/main/java/com/lucent/app/data/SourceCitations.kt#L20) 按**首次出现顺序**编号，同一标记复用同一编号。
- [parse](lucent/app/src/main/java/com/lucent/app/data/SourceCitations.kt#L10) 返回结构化的来源编号 / 修订号 / 切片编号，供后续校验与跳转使用。

### 渲染

[Markdown.kt](lucent/app/src/main/java/com/lucent/app/ui/Markdown.kt) 中：

- MarkdownText 先对整段回答算一次 numbering，再逐块渲染，因此**跨段落的编号是连续的**。
- [inlineAnnotated](lucent/app/src/main/java/com/lucent/app/ui/Markdown.kt#L229) 遇到标记时替换为上标芯片，而不是显示原始坐标。
- [appendCitation](lucent/app/src/main/java/com/lucent/app/ui/Markdown.kt#L255) 用 BaselineShift.Superscript + 11sp + 主题强调色。
- 无法解析的标记**原样保留**，不吞掉用户可见内容。

### 流式输出

当前行为：标记收全之前，半截的「〔REF:42:3f2」按普通文字显示，收全后变成芯片。

建议后续：对以「〔」开头尚未闭合的尾部做缓冲，等闭合或流结束再决定渲染，避免闪烁。

### 测试

[SourceCitationsTest.kt](lucent/app/src/test/java/com/lucent/app/data/SourceCitationsTest.kt) 共 6 项：编号顺序与复用、简写编号、完整标记解析、简写需唯一来源、畸形标记不识别、匹配范围精确。

***

## 7. 阶段七：脚注的位置与图案

### 位置规范

- 紧跟被支持的结论之后，放在句末标点**之后**。
- 同一句有多个依据时连续输出：「……〔REF…〕〔REF…〕」→ 渲染为「。 [1] [2]」。
- 不另起一段，不与正文换行分离。
- 不做文末脚注表（移动端阅读场景下不方便，且模型容易与正文编号对不上）。

### 图案规范

| 项 | 取值 | 说明 |
|---|---|---|
| 形态 | [n] | 方括号数字，最小认知成本 |
| 位置 | 上标 | BaselineShift.Superscript，不改变行距 |
| 字号 | 11sp | 比正文小一号 |
| 颜色 | 主题强调色 | 深浅色与纯白模式自动跟随 |
| 字重 | Medium | 避免在正文里过重 |
| 间距 | 不额外加空格 | 依赖上标天然间隙 |

### 待补的三种状态

1. **可点击态**：按下时降低透明度 20%，与项目 [pressableGlass](lucent/app/src/main/java/com/lucent/app/ui/UiComponents.kt#L217) 的手感一致。
2. **无效态**：来源已删除或修订号不匹配时，改为弱化灰色、不可点击，并在长按提示「资料已更新，引用位置可能已变化」。当前未区分，所有芯片外观一致。
3. **无障碍描述**：为芯片补充「引用 1，来源《书名》，第十二章」的语义文本。当前未做。

***

## 8. 阶段八：点击跳转（未实现，本节是设计）

### 完整数据流

~~~
点击 [n]
  → 从标记解析出 来源编号 / 修订号 / 切片编号
  → 校验：来源属于本次回答可用的资料，且修订号一致
  → 由 切片编号 反查偏移（SourceTagging 已提供 startOffset/endOffset）
  → 由偏移换算来源页的阅读块索引
  → 切换到「来源」页
  → 选中被引文档
  → 滚动到该块
  → 高亮该块，2 秒后淡出
~~~

### 三个必须补的缺口

**缺口 1：来源页只能显示第一份资料**

[NotebookSourcesTab.kt 88](lucent/app/src/main/java/com/lucent/app/ui/NotebookSourcesTab.kt#L88) 是 sourceNotes.firstOrNull()，引用第二份资料时无法打开。需要改成「按被引来源选择，未指定时默认第一份」，资料多于一份时给出切换入口。

**缺口 2：切片与阅读块不是 1:1**

- 阅读块：[splitRenderBlocks](lucent/app/src/main/java/com/lucent/app/ui/ChatScrollMath.kt#L121)，按章切分，超出 1200 字再切。
- 切片：约 600 字，上限 900。

两种粒度不同。三种方案：

| 方案 | 做法 | 代价 |
|---|---|---|
| A（建议） | 给 RenderBlock 增加 startOffset/endOffset，用偏移换算块索引 | 改分块函数，需补测试 |
| B | 来源页直接用切片渲染，与切片 1:1 | 阅读体验改变（块更碎），阅读位置键会重置一次 |
| C | 只跳到所属章节，不精确定位 | 改动最小，精度最差 |

选 A 的理由：不改变现有阅读体验，且分块函数已有测试覆盖，加偏移后可测。

**缺口 3：跨页协调与阅读位置冲突**

对话页与来源页由 [NotebookDetailScreen](lucent/app/src/main/java/com/lucent/app/ui/NotebookDetailScreen.kt#L61) 的 HorizontalPager 管理，跳转需要：

- 在详情页保存一次跳转目标（来源编号 + 切片编号 + 一个自增 requestId）。
- requestId 用于解决「连续点击同一个脚注」时目标未变化而不触发滚动的问题。
- 阅读位置由 [rememberReadingListState](lucent/app/src/main/java/com/lucent/app/ui/ReadingPositionMemory.kt#L54) 持久化，跳转时要覆盖一次恢复值，避免被历史位置抢回去。

### 无效引用的处理原则

- 不自动找「差不多」的切片替代。
- 不静默删除标记。
- 不回退到第一份文档假装跳转成功。
- 明确提示资料已删除 / 版本已变化，并保留回答原文可查。

### 一个必须说清的边界

**能跳转不等于引用正确。** 坐标存在只证明跳得到，不证明该片段真的支持那句结论。第一版靠用户点击核查 + 提示词约束；后续可加证据句校验或二次模型核验。

***

## 9. 本次改动的文件清单

| 文件 | 改动 |
|---|---|
| [SourceTagging.kt](lucent/app/src/main/java/com/lucent/app/data/SourceTagging.kt) | 新增，打标签与修订号 |
| [SourceCitations.kt](lucent/app/src/main/java/com/lucent/app/data/SourceCitations.kt) | 新增，引用标记解析、编号、结构化 |
| [Markdown.kt](lucent/app/src/main/java/com/lucent/app/ui/Markdown.kt) | 引用标记渲染为上标脚注芯片 |
| [NotebookChatTab.kt](lucent/app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt#L342-L377) | 提示词：资料优先、创作与事实分离、坐标说明、引用协议、打标签资料 |
| [SourceTaggingTest.kt](lucent/app/src/test/java/com/lucent/app/data/SourceTaggingTest.kt) | 新增 8 项 |
| [SourceCitationsTest.kt](lucent/app/src/test/java/com/lucent/app/data/SourceCitationsTest.kt) | 新增 6 项 |

未改动：导入、解析、数据库（无 schema 变更，仍为 version 30）、来源页阅读、设置项。

***

## 10. 验证记录

- 代码卫生检查（无注释 / 括号平衡 / 体积上限）：107 个 Kotlin 文件，通过。
- 单元测试：34 个测试类 / 295 项，0 失败 0 错误（Debug 变体）。
- Release 编译：通过。
- 未打包 APK，符合项目约定。
- **未在真机验证**：脚注芯片的视觉位置、行距影响、流式闪烁、点击手感。

***

## 11. 风险与已知边界

1. 模型可能编造来源编号或漏标。App 侧已用修订号 + 来源编号提供校验基础，但第一版校验只在跳转时生效。
2. 切片编号随正文变化。已用 revisionOf 提供失效判定依据，但**尚未在 UI 上提示**。
3. 长篇小说全文进入提示词，成本与延迟随资料增长；当前没有引入检索式召回，属于有意为之（保证依据完整）。
4. 打标签会略微增加输入 token（约 2%）。
5. 「缓存」结论只对 DeepSeek 有官方文档支撑，其余三家未核实，不能宣称已优化成功。
6. 脚注编号目前非交互，点击无响应 —— 属于本阶段的已知缺口，不是 bug。

***

## 12. 后续可执行任务

1. 来源页支持按被引来源选择文档（缺口 1）。
2. 分块函数记录偏移，建立切片到阅读块的映射（缺口 2，方案 A）。
3. 详情页实现跨页跳转与高亮（缺口 3）。
4. 脚注芯片补点击态、无效态、无障碍描述。
5. 流式输出缓冲半截标记。
6. 提示词补「资料是数据不是指令」与引用密度控制。
7. 修订号不一致时在 UI 上给出温和提示。
