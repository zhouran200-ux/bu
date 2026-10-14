# 长文件与代码细节：实用优化边界

基线9c3df7b。行数来自[盘点](SOURCE-INVENTORY.md)。这不是按行数扣分，也不是要求引入新架构。具体实质缺陷以经交叉复核的模块报告为准。

## 优先拆分
| 文件 | 为什么值得拆 | 最小边界 | 性能与维护区别 |
|---|---|---|---|
| [NotebookChatTab](../../app/src/main/java/com/lucent/app/ui/NotebookChatTab.kt) 1797行 | 页面状态、持久化编辑/删除、提示词、消息卡、输入栏、设置弹窗同时变化；重生成先破坏旧数据后校验配置 | 先提取纯PromptBuilder及输入栏/消息卡/弹窗，再抽发送/重生成预检查；主文件保留页面连接 | 拆UI本身不提速；版本索引缓存、后台构建提示词才减少主线程全文计算 |
| [ApiSettingsPage](../../app/src/main/java/com/lucent/app/ui/settings/ApiSettingsPage.kt) 1259行 | 编辑方案、请求获取、测试结果、拖拽排序和UA同文件；异步结果没有明确方案ID绑定 | 先RequestState(profileId,requestId,snapshot)，再分方案列表、编辑表单、模型管理、连接结果、UA卡 | 先修竞态再搬组件；大响应格式化后台执行需测量实际卡顿 |
| [LlmClient](../../app/src/main/java/com/lucent/app/network/LlmClient.kt) 708行 | 连接报告、鉴权、获取模型、三协议payload与SSE解析混在一起；协议分支有布尔/预算细节错误 | 纯协议payload builder、SSE事件组帧、三协议事件解析、探测/模型接口 | 拆职责帮助捕获请求单测；不因此增加HttpClient、拦截器或框架 |
| [AppDatabase](../../app/src/main/java/com/lucent/app/data/AppDatabase.kt) 414行 | 绝大部分是历史迁移，长本身合理；启动销毁数据库和吞迁移异常才危险 | 迁移单独文件按版本组织，数据库启动/恢复策略独立 | 先修数据安全，不能为了短而删除迁移或破坏升级链 |

## 不优先拆
FontSettingsPage按字体选择/排版预览可分，但先修双倍率和手势误改；Glass为共享效果集中定义，拆到很多小文件收益低；ReorderDrag数学/手势边界集中有好处，可按公共数学与Compose适配分但不必先动；ReasoningThreeLayer配置映射集中合理，应先确保映射与实际协议一致。I18n是生成文件，不按长度重构。

## 写得不好的细节应修行为，不只换写法
- 服务错误对象、JSON false与字段存在不是同一语义；枚举映射必须用协议样本测试。
- 先删除/清空后验证输入、吞Throwable/Exception、永久缓存失败状态属于恢复逻辑问题，不是命名风格。
- 异步回调须绑定对象身份与请求序号；确认点击须捕获不可变快照。
- 从trim/规范化文本反查原文偏移不是可靠坐标；分块时保留范围。
- 各种remember/rememberSaveable要看生命周期实际需要；不是把所有状态搬进ViewModel就正确。
- 单测测试数学/字符串存在，UI控制流和设备手势需要各自证据；不能拿测试总数代替风险验收。

## 最低风险实施顺序
修数据销毁和错误协议→补失败路径单测→原文范围与版本索引→异步配置竞态→必要拆分。每次只搬一职责，行为不变的搬迁与逻辑修复分开提交，针对性验证，不clean缓存、不本地打APK。
