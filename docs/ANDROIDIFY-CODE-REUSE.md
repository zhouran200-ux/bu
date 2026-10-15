# Androidify实际源码移植

本轮实际复制并改编[ScaleIndication.kt](https://github.com/android/androidify/blob/931cfdd682279f15bdc63a15420f2bda9a4ac712/core/theme/src/main/java/com/android/developers/androidify/theme/components/ScaleIndication.kt)，固定commit931cfdd682279f15bdc63a15420f2bda9a4ac712，不再仅参考思路。

保留IndicationNodeFactory/DrawModifierNode、Press/Release/Cancel的collectLatest以及draw阶段读取Animatable。适配包名、缩放由1.04改0.985避免卡片溢出；factory按参数比较避免无意义重启；onReset重置复用节点；系统关闭动画时target1+snap。公共GlassButton/pressableGlass已使用新indication，移除旧平行graphicsLayer缩放，保留原填充/触感/点击/禁用语义。不宣称全控件、导航或弹层均已移植。

Apache2头与修改声明原位保留，源码零注释检查只对本文件package前许可区段作限定例外，正文仍检查。APK assets/licenses包含完整Apache2与NOTICE；docs/THIRD-PARTY-NOTICES更新来源。没有复制商标或美术。

导航继续由现有pager唯一owner处理；Material ModalBottomSheet、LazyColumn、verticalScroll、draggable本来就是AndroidX开源实现的实际库复用，不为抄代码再次手写。系统窗口内部/私有API不搬。后续只有有收益且兼容时再适配导航/弹层实现。

验证：两项factory/节点reset JVM测试通过，Release编译通过。工具契约覆盖源码归属/Cancel/draw算法与实际调用。未真机验证按压抢滚动/禁用中断，也不承诺120fps。本轮不改背景主题/依赖/数据库/导航栈。
