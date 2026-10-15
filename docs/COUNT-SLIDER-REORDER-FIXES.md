# 来源计数、滑块误触与边缘自滚动修复

基线6e9d237。

1. 首页空本显示真实0个来源，不再coerceAtLeast(1)，只对异常负数防御为0。
2. 步进轨道down不改值；总位移超过touchSlop才横/纵仲裁，对角线平手优先页面滚动。横向锁定才消费并更新，静止点击抬手生效；取消、父级消费和销毁回滚初始草稿，不调用finished保存。加减按钮和外观保留。
3. 笔记本采用列表级长按手势+抓取overlay，原item可正常虚拟化，不以原item持续可见为前提。边缘72dp区域速度随接近程度平方增长，上限900dp/s，帧时间截到50ms；停手持续滚动、实际consumed同时补偿sourceTop/dragY保持抓取位置。每帧更新可见插槽，松手落位，取消/离开不提交。
4. API方案列表在SettingsScroll viewport真实root bounds下处理，root指针与实际scroll消费量补偿位移/交换。仅x离开区域暂停，上下越界维持封顶速度。取消/失焦页面退出丢弃临时顺序，正常松手才保存。原统一行高假设保留，需实际大字验收。

## 清理审计
检索coerceAtLeast(1)/sourceCount、Initial/down commit/旧指针循环、reorderableItem/topOf/reorderModifier、长按/scrollBy与父滚动边界。旧item级手势及死helper已删除；列表滚动记忆、settle、排序保存和公共效果保留。无数据库/Manifest/依赖/资源新增，主题、引用、后台生成不改。新增单测覆盖真实0、方向边界、边缘速度/无效数值、抓取补偿、取消和离屏落位。源码契约不代替真实触摸。

## 验证
全量JVM与Release Kotlin编译通过；Python契约、源码卫生、嵌套滚动和cr-at-eol diff检查通过。未本地打包APK、未真机验收。设备需测试：轨道上纵滑不变值、轻点/横拖/取消、长列表首项到末尾及反向、停在边缘不动仍滚、松手立即停、切页取消、字号放大与前后padding下抓取位置。

上轮6e9d237的check与APK均completed/success；不代表当前提交云端结果。
