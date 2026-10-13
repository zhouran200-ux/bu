<div align="center">

# 𝓛𝓾𝓬𝓮𝓷𝓽

### 现代 · 极简 · 内敛至近乎沉默

**一款纯粹专注的 Android 笔记本应用，内置云端大模型知识库问答。数据全部留在设备上。**

</div>

完整说明（架构、功能、致谢与第三方许可）见 **[`docs/README.zh-CN.md`](docs/README.zh-CN.md)**。

---

## 本地构建

需要 JDK 17 与 Android SDK（compileSdk 36、minSdk 28），无需其它工具：

```bash
./gradlew :app:assembleDebug        # 调试包，使用仓库自带的 debug.keystore
./gradlew :app:testDebugUnitTest    # 单元测试
./gradlew :app:lintRelease          # 静态检查
```

Windows 使用 `gradlew.bat` 执行同名任务。对于本地编码与 Gradle 编码不同的 Windows 环境，单测会自动生成类路径清单 JAR，避免中文目录名被 Java 启动器误读；无需移动项目或更改系统编码。清单位于纯 ASCII 路径的 Gradle 用户目录（否则使用临时目录）下的 `lucent-test-classpath`，按项目隔离；测试报告仍在 `app/build/reports/tests`。

## 云端构建

推送到 `main` 即会自动运行 `build-apk.yml`，产出可直接安装的 APK 并附上构建产物。要打签名 Release 包，在仓库 Secrets 里配置 `LUCENT_KEYSTORE_BASE64` 与 `LUCENT_KEYSTORE_PASSWORD`。
