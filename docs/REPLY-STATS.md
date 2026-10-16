# 回复信息栏统计口径

顺序固定：首字 → 用时 → 缓存命中/输入token总数T → 字数 → 本地HH:mm。数字来自实际生成与服务回执，示例不是默认值。

首字：开始调用流接口至首个非空白正文chunk；思考chunk不算正文。用System.nanoTime单调时钟计时，不受调整系统时间影响。用时从同一起点到流成功/失败/取消结束，不含初始消息数据库等待和最终落库；保留0.1秒精度，低于0.1秒显示<0.1秒。没有正文的失败/纯思考不补首字。

缓存分母是prompt/input token总数，包括被缓存的输入，不混入completion；只有输入token能参与前缀缓存。已知0显示0/10000T，缺字段显示—/10000T，全部usage缺失时不显示缓存项。不以字数估算token，不缩写k/M。OpenAI/DeepSeek/Anthropic/Google回执字段均保留是否提供缓存数据的标志。

字数采用回复正文非空白Unicode码点数，不含思考和REF坐标；标点/Markdown源码符号仍计数，不声称分词器或完全渲染后文字统计。emoji代理对计一个码点。时间使用消息timestamp在本地时区显示，保持原时间与消息排序，不因刷新/编辑/重生成随意重排。

新增Room31和30→31迁移：firstTokenMillis、generationMillis可空；cachedTokensKnown默认false。旧记录保留旧总秒数回退，无首字就不显示；旧缓存正数可使用，旧0无法证明是未知还是0，显示—。历史schema30不改。新信息load/save均映射持久化。

验证：38类320项JVM测试零失败；Release Kotlin和设备迁移测试源码编译通过，30项Python契约和117文件卫生通过。设备迁移仅编译未执行，未本地打包APK。首次并行Debug/Release KSP写schema时出现EOF，串行生成后schema31完整且编译测试通过；未clean。

残留审计：compactCount只用于旧统计已移除；旧显示测试更新。检查answerSeconds、UiChatMessage、ChatCompletionResult、StreamUsage、数据库版本注册与会话映射，没有改旧计时字段的历史数据。原思考区使用旧秒数字段保留，未把总时间冒充新首字。
