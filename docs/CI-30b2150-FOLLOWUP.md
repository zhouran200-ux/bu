# 30b2150 云端失败跟进

上轮 check 失败，APK 构建成功，不能合并称为验证通过。

## Release JVM 任务

实际 Gradle 日志显示 testReleaseUnitTest 不存在，任务选择阶段退出。AGP 9.1.1 源码的 BooleanOption 定义 android.onlyEnableUnitTestForTheTestedBuildType 默认 true（AGP 9 默认仅启用被测试构建类型的单测）。添加 false，保留 Debug/Release 两套单测和报告检查，不删除覆盖。

依据：https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.1.1/gradle-9.1.1-sources.jar

## Room 插桩运行时

上轮 Android 15 模拟器 26 项迁移测试有 22 项通过，4 项在加载 schema 时因 GeneratedSerializer.typeParametersSerializers AbstractMethodError 失败，尚未执行到迁移 SQL。

Room migration-jvm 2.8.4 公开依赖 serialization-json-jvm 1.8.1，后者依赖 core 1.8.1；SavedState 1.3.3 则声明 core 1.7.3。只读分析发布 JAR 表明该方法在 core 1.7.3 是 abstract，在 1.8.1 是默认实现，Room FieldBundle serializer 未覆盖该方法。因此向目标 Debug APK 加入 core 1.8.1，让应用运行时与测试库所需 ABI 对齐，而不是只在独立测试 APK 中升级。Release 依赖不变。

这是有二进制证据的修复方向，不等于已证明本轮云端通过。工作流新增 app/test runtime 的 dependencyInsight 报告，保留原有全部 schema 测试，下一轮根据解析版本和模拟器结果复核。

依据：
- https://dl.google.com/dl/android/maven2/androidx/room/room-migration-jvm/2.8.4/room-migration-jvm-2.8.4.pom
- https://dl.google.com/dl/android/maven2/androidx/savedstate/savedstate-android/1.3.3/savedstate-android-1.3.3.pom
- https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-jvm/1.8.1/kotlinx-serialization-json-jvm-1.8.1.pom

本轮未启动本地 Java/Gradle，推送后不等待 CI。未更改生产迁移 SQL、schema、测试断言、Lint baseline 或签名配置。
