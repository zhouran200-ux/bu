# 输入法弹出时保持对话底部

基线6a1530e。旧IME effect在opened且followStream时延迟50ms，animateScrollToItem(messages.size-1)，该索引既不含header/footer，offset又为0，长消息会跳到条目开头。移除该路径。

新ChatViewportAnchor记录前次实际viewport高度及是否贴底；高度缩小且此前贴底时requestScrollToItem到真正footer。输入法逐帧缩小时继续贴底；用户向上阅读时不追底；UserInput滚动立即清除贴底锁；高度增大不主动跳转。没有固定延时，不依赖是否正在生成。

验证：新增4项anchor状态测试（逐帧缩小、读历史、主动滑动取消、初始/增大视口）及既有ReadingPosition测试通过；Debug/Release Kotlin编译通过。未本地打APK，未真机验收。需设备覆盖超长末条回复、短消息、滚到历史中段、输入法候选栏高度变化、收起再弹、流式过程中打断滚动。

残留审计：搜索previousImeVisible、opened、delay50、IME scroll effect；旧按messages.size-1定位IME路径已删除；保留主动发送/历史定位/流式追底的业务行为，未改模型或数据库。

上轮6a1530e：APK#98及check#35均completed/success；这不是当前提交结果。
