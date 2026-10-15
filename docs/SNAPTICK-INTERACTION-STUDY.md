# SnapTick交互研究及Lucent适配

固定研究zhouran200-ux/snaptick提交02dfb269a472304ff09e2cde804e61d265dbb5f3。该fork README指向原作者vishal2376。

## 核实源码
- [ThemeSelector](https://github.com/zhouran200-ux/snaptick/blob/02dfb269a472304ff09e2cde804e61d265dbb5f3/app/src/main/java/com/vishal2376/snaptick/presentation/common/components/ThemeSelector.kt)：按压/选中分离、背景色过渡、选中上浮和勾标渐显。
- [PriorityComponent](https://github.com/zhouran200-ux/snaptick/blob/02dfb269a472304ff09e2cde804e61d265dbb5f3/app/src/main/java/com/vishal2376/snaptick/presentation/add_edit_screen/components/PriorityComponent.kt)：局部选中短线长出，不是共享跟手pill。
- [AppNavigation](https://github.com/zhouran200-ux/snaptick/blob/02dfb269a472304ff09e2cde804e61d265dbb5f3/app/src/main/java/com/vishal2376/snaptick/presentation/navigation/AppNavigation.kt)：设置入口向上过渡；不移植其业务导航。
- [SettingsScreen](https://github.com/zhouran200-ux/snaptick/blob/02dfb269a472304ff09e2cde804e61d265dbb5f3/app/src/main/java/com/vishal2376/snaptick/presentation/settings/SettingsScreen.kt)：统一ID选择底部弹层，Material组件；不是所有列表有stagger。

## 本轮实际适配
研究交互目标后独立实现，没有复制/翻译GPL源码或资产。
1. selectedCard由瞬时插入背景改为160ms色态过渡，取消选中也能淡出；动画State在drawBehind内读取，不让整行布局逐帧重排。关闭系统动画则snap。现有API方案/模型与新增主题选择共用，不加持续动画。
2. 主题整行可选，Radio仅展示状态与行合并，避免两套点击逻辑；选中背景柔和出现，保留既有主题锁定/保存，不改色板。
3. 模型方案展开使用同一chevron旋转至90度，值仅layer读取；不再瞬间替换两个图标。模型选中背景同一helper。保持列表item/key/初始位置和仅横杠下拉规则，不新增对号/整卡放大。

## 许可边界
[LICENSE](https://github.com/zhouran200-ux/snaptick/blob/02dfb269a472304ff09e2cde804e61d265dbb5f3/LICENSE)是GPLv3。未经用户选择相应发布许可，不直接复制进Lucent并发布APK。本轮借鉴一般交互目标并使用既有Compose API独立实现，不宣称实际移植SnapTick源码。之前Androidify ScaleIndication仍是有归属/许可证的真实源码复用。

## 审计与验证
主题/模型选态原瞬变路径与双点击处理已替换；旧KeyboardArrowDown import删除；selectedCard API兼容已有调用，不删公共函数/迁移/偏好字段。背景主题算法、导航、数据库、Manifest、依赖和后台服务不变。全量JVM测试及Release Kotlin编译通过；补源码契约验证draw阶段、双向颜色过渡、Radio单owner及同图标旋转。未真机验收手感，不承诺120FPS。
