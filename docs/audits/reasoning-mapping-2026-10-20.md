# 思考档位映射核验（2026-10-20）

## 日期、来源与边界

核验时间：2026-10-20（Asia/Shanghai）。对照在线官方文档，不以工作区旧 HTML/snippet 作为最新标准。部分 web_fetch 403/跳转，使用系统现有代理从相同官方 URL 取得实时 HTTP 200 完整 Markdown；Google Generate Content 文档更新时间为 2026-10-14。原缓存保留但不改写成“官方新标准”。

没有使用付费 API key 实发模型请求。下面的“合法/支持”指官方协议文档与本地序列化检查，不能保证任意中转、区域、账户、退役模型仍可用。

## 外层文件归档

工作区根目录的 33 个散落文件（7,982,841 bytes）集中到 workspace-artifacts，按 reference-pages（16）、reference-notes（7）、history（1）、verification-logs（9）分类；逐文件移动前后 SHA-256 一致。根目录现在只剩目录。应用源码 lucent、已有 diagnostics 和 actions-audit 目录保留原位置。迁移清单和导航位于 workspace-artifacts/README.md 与 relocation-manifest-2026-10-20.json。

这些文件在 Git 仓库外，不纳入应用构建或上传。sha-map 是历史提交对应表，不是思考档位表；openai-chat-ref.html 仅含 Not found，保留溯源但不是有效文档。旧日志保持原编码，包括无法作为 UTF-8 读取的输出。未删重复文件、未重写旧资料；已搜索项目/诊断脚本引用，未发现依赖这些根目录路径的运行代码。

## 映射规则

应用五档“自动/极速/标准/深度/极致”是产品抽象，官方没有跨厂商统一的五档标准。AUTO 不发思考覆盖参数，显示“模型默认（不覆盖）”；默认可能是关闭、固定 high/max 或自适应。FAST 保持低思考而不是关闭，MAXIMUM 选模型已声明的最高有效强度；四个手动档映射成两个/三个有效强度时会明确展示重复值。

| 模型族 | FAST | BALANCED | DEEP | MAXIMUM | 主要字段/处理 |
|---|---|---|---|---|---|
| GPT-5 / 5.1 | low | medium | high | high | Chat Completions reasoning_effort；不能发max |
| GPT-5.2 / 5.4 / 5.5 | low | medium | high | xhigh | 不能发max |
| 已核验GPT-5.6 / GPT-6 / 6.1型号 | low | medium | high | max | mode:pro是Responses另一个轴，不等于max |
| Claude 3.7历史 / 4 / 4.1 / Sonnet&Haiku4.5 | 2048 | 8192 | 16384 | 32768 | thinking.enabled + budget_tokens；3.5及更老不伪造thinking |
| Claude Opus4.5 | low+2048t | medium+8192t | high+16384t | high+32768t | effort与budget是正交控制，必须同时保留 |
| Claude Opus/Sonnet4.6 | low | medium | high | max | thinking.adaptive + output_config.effort；无xhigh |
| Claude Opus4.7/4.8、已核验5.x | low | medium | high | max | 五档effort；5.x默认已on，显式adaptive同时请求摘要 |
| Gemini 2.5 Pro | 1024 | 8192 | 16384 | 32768 | thinkingBudget；上调MAX是本地策略，不是原24576非法 |
| Gemini 2.5 Flash / Flash-Lite | 1024 | 8192 | 16384 | 24576 | thinkingBudget；Lite AUTO默认不思考 |
| Gemini 3.1Pro / 3.8Flash / 4Argon | LOW | MEDIUM | HIGH | HIGH | thinkingLevel |
| Gemini 3ProPreview | LOW | LOW | HIGH | HIGH | 只支持LOW/HIGH |
| Gemini 3.1Flash-LiteImage | MINIMAL | HIGH | HIGH | HIGH | 只支持MINIMAL/HIGH |
| DeepSeek V4/V4.1已核验型号及alias | low | high | high | max | reasoning_effort；默认思考开启 |
| Qwen3.8已核验型号 | low | medium | xhigh | xhigh | reasoning_effort；不能与thinking_budget同传 |
| Qwen已核验旧Plus/Flash及3.5/3.6/3.7预算族 | 2048 | 8192 | 16384 | 32768 | enable_thinking:true + thinking_budget（本地保守预算策略） |
| GLM5.3系列 | low | high | high | max | 原厂三档 |
| GLM5.2 | high | high | high | max | 官方low/medium归high、xhigh归max，仅列有效强度 |

Gemini 3/3.6Flash与3.1/3.5Flash-Lite的MINIMAL/LOW/MEDIUM/HIGH能力保留，FAST仍取LOW；3.8Flash与4Argon不能发送MINIMAL。仅已核验exact型号与其严格有效日期后缀继承能力，保留compact/前缀匹配。未知future、coder、chat-latest、image/live/audio及不明中转别名不靠家族关键词猜参数。NONE/no override表示“未确认可配置控制”，不等于模型没有推理能力。

## 本轮确证修复

1. **GPT-5版本混用**：旧代码给全部GPT5/6相同max列表，现按官方分代，GPT6/current max保留。
2. **Claude旧代分类错误**：3.5错误当预算、3.7错误当effort、所有4.x错误一刀切。改用明确型号表，4.5 Opus与4.6+启用方式分开。
3. **预算不是输出长度**：旧Claude兼容分支将claudeBudgetTokens写max_completion_tokens并不启思考；改为thinking.enabled/budget_tokens并另留max_tokens输出空间。
4. **Gemini兼容丢参**：原resolver产出Gemini字段却被OPENAI分支丢弃；改为官方REST形状 extra_body.google.thinking_config.thinking_level/thinking_budget，不同传reasoning_effort。原生用generationConfig.thinkingConfig。非AUTO请求includeThoughts/include_thoughts摘要，不宣称因此才启思考。
5. **Qwen跨代猜effort**：Qwen3.8当前三档保持，明确旧预算型号用enable_thinking/thinking_budget；不把Omni/Coder/QVQ全部猜成effort。
6. **DeepSeek历史跨代**：不再把任意含r1的名称套V4 low/high/max。历史R1原厂model曾为deepseek-reasoner，2026官方已退役；未实测R1会400，只取消无证据覆盖。
7. **GLM5.2显示假区分**：仅展示有效high/max，不把兼容别名当五种独立思考强度。
8. **AUTO误导文案与未知metadata**：不再叫“原厂自适应”；明示supported=false或未知control不自动推成当前协议effort。

## 协议与第三方边界

- 原生Claude使用output_config.effort，按型号补enabled预算或adaptive；AUTO不覆盖。默认max_tokens=8192仍是保守产品策略，并非官方无限推理承诺。
- 官方 api.anthropic.com 的 OpenAI兼容文档明确reasoning_effort被忽略。因此该host下effort-only选择显示需切换原生协议、不伪装成功；同时有预算（Opus4.5）时保留文档支持的thinking预算扩展。普通relay保留原有effort约定但标“兼容扩展，依赖服务端支持”，不宣称官方保证生效。
- Google官方兼容端点的extra_body是HTTP字段，只有一层；Qwen的SDK extra_body只是参数包装，手工HTTP使用顶层enable_thinking/thinking_budget，不能混用。
- 同名模型在百炼/原厂可能不同，当前registry的显式远端metadata仍为进程级model表；完整按host隔离、任意网关转换、Responses API pro mode、额外模型族不在本轮实现范围。未知少发字段，已知兼容风险明示，不伪称全平台支持。
- Claude新版思维文本常默认omitted，Gemini includeThoughts返回的是摘要而非原始链；非AUTO显式请求摘要是一项展示策略。AUTO继续不添加thinking override，不能据“没有收到摘要”推导模型没有思考。

## 官方来源

- OpenAI [Reasoning guide](https://developers.openai.com/api/docs/guides/reasoning.md)、[GPT-5](https://developers.openai.com/api/docs/models/gpt-5.md)、[5.1](https://developers.openai.com/api/docs/models/gpt-5.1.md)、[5.2](https://developers.openai.com/api/docs/models/gpt-5.2.md)、[5.4](https://developers.openai.com/api/docs/models/gpt-5.4.md)、[5.5](https://developers.openai.com/api/docs/models/gpt-5.5.md)、[5.6](https://developers.openai.com/api/docs/models/gpt-5.6.md)、[6 Astra](https://developers.openai.com/api/docs/models/gpt-6-astra.md)、[6.1 Sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol.md)。
- Claude [Effort](https://platform.claude.com/docs/en/build-with-claude/effort.md)、[Thinking](https://platform.claude.com/docs/en/build-with-claude/thinking.md)、[Extended thinking](https://platform.claude.com/docs/en/build-with-claude/extended-thinking.md)、[OpenAI SDK compatibility](https://platform.claude.com/docs/en/cli-sdks-libraries/libraries/openai-sdk)、[Models](https://platform.claude.com/docs/en/models/overview.md)、[退役表](https://platform.claude.com/docs/en/about-claude/model-deprecations.md)。
- Gemini [Generate Content thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking)、[Gemini 3](https://ai.google.dev/gemini-api/docs/generate-content/gemini-3)、[OpenAI兼容](https://ai.google.dev/gemini-api/docs/openai)、[ThinkingConfig](https://ai.google.dev/api/generate-content#ThinkingConfig)、[2.5 Flash Image](https://ai.google.dev/gemini-api/docs/models/gemini-2.5-flash-image)。当前默认thinking页面已偏向Interactions，不能将其字段直接套入streamGenerateContent。
- DeepSeek [思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)、[Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion)、[更新日志](https://api-docs.deepseek.com/updates)、[R1发布](https://api-docs.deepseek.com/zh-cn/news/news250120/)。
- Qwen [官方平台思考](https://platform.qianwenai.com/docs/developer-guides/text-generation/thinking.md)、[API](https://platform.qianwenai.com/docs/api-reference/chat/openai-chat.md)、[百炼API](https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions.md?mode=pure)、[Omni](https://help.aliyun.com/zh/model-studio/qwen-omni.md?mode=pure)、[预算错误码](https://help.aliyun.com/zh/model-studio/error-code.md?mode=pure)。3.5/3.6/3.7官方最大思维链远高于部分本地档位；32768不是声称厂商硬上限。
- 智谱 [思考能力](https://docs.bigmodel.cn/cn/guide/capabilities/thinking.md)、[GLM5.3](https://docs.bigmodel.cn/cn/guide/models/text/glm-5.3)、[Flash/FlashX](https://docs.bigmodel.cn/cn/guide/models/vlm/glm-5.3-flash.md)。

## 上一轮API35与本轮测试加固

上轮dcc9660的 [check](https://github.com/zhouran200-ux/bu/actions/runs/39312419125) 在API35有9例5000ms超时（6剪贴板/3系统Back），API33/JVM/source成功；[APK构建](https://github.com/zhouran200-ux/bu/actions/runs/39312419123)成功但Preview因门禁失败未发布。日志403/报告401，无法获得完整stack，不能称已确证根因。

仅加固4个设备测试及SystemInteractionFixture：唤醒非安全测试设备、确认真实Activity/Dialog窗口焦点、排除遗留IME、使用host剪贴板上下文并清理残留，仍使用真实剪贴板和系统Back，保留原断言与5000ms等待。失败时输出系统状态；不是增大超时或跳过测试。实际API35结果留云端复核，不承诺本次已恢复。

## 验证与残留审计

定向 JVM 7 套共 64 项通过，0 跳过/失败/错误；主代码与设备测试代码编译通过。Python 工具/契约共129项，123项通过、6项因无可用Bash而跳过；quick10项通过，275个Kotlin文件、294个源码/配置文件审计0错误/候选，git diff --check通过。最终增量Lint通过，保留已有警告/提示，不宣称零警告。设备fixture未本地执行；不将API35修复说成已验证恢复。

复核补修：已知模型的远端部分metadata缺省levels/thinking_type/budget_max时继承同control已核验值，不因拉取模型目录丢掉GPT5合法档位或Claude启用方式；所有预算档位都受显式预算上限约束。旧退役Claude4/4.1的普通输出上限未取得本轮fresh证据，不臆造上限或声称已实测合法；这仍属于需具体托管端确认的历史兼容边界。

模型目录、mapper、payload和设置摘要分别维护自己的职责；移除旧版本Double推断及旧错误budget→max_completion_tokens路径，更新固化错假设的测试。全局搜索控制enum/新字段/旧符号，未知metadata安全降级。无新增网络服务、数据库、权限、后台任务或生产剪贴板/返回行为改动。保留旧网页用于历史，不伪造其内容。

本地只编译/单测/Lint，不产出APK/AAB；本轮推送后不等待云端结果。未经真实端点调用的字段只能说序列化与官方文档一致，不说服务端实测通过。
