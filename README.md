<div align="center">

# 𝓛𝓾𝓬𝓮𝓷𝓽

### 现代 · 极简 · 内敛至近乎沉默

**一款纯粹专注的 Android 笔记本应用，内置云端大模型知识库问答。数据全部留在设备上，封存在加密数据库里。**

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

## 云端构建

推送到 `main` 即会自动运行 `build-apk.yml`，产出可直接安装的 APK 并附上构建产物。要打签名 Release 包，在仓库 Secrets 里配置 `LUCENT_KEYSTORE_BASE64` 与 `LUCENT_KEYSTORE_PASSWORD`。
