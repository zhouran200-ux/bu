<div align="center">

# 𝓛𝓾𝓬𝓮𝓷𝓽

### 现代 · 极简 · 内敛至近乎沉默

**一款纯粹专注的 Android 笔记本应用，内置云端大模型知识库问答。数据全部留在设备上。**

</div>

完整说明（架构、功能、致谢与第三方许可）见 **[`docs/README.zh-CN.md`](docs/README.zh-CN.md)**。

---

## 验证与交付

本地不启动 Java/OpenJDK、Gradle、编译、Android 单元测试、Lint 或 APK 打包。修改代码并补充测试后，只运行轻量 Python 检查：

```bash
python tools/verify.py quick
python -m unittest discover -s tools/tests -v
```

`verify.py` 默认采用 `quick`，本地请求 `logic` / `full` 会直接拒绝，不启动 Gradle。完整验证在 GitHub Actions 的 `check` 工作流执行：Debug/Release 编译与 JVM 单测、两种变体的 Lint、Room schema、Android 插桩测试编译及云端模拟器运行。报告无论成功失败都尽量上传为 artifacts。

推送成功后结束本轮，不等待 CI；下一次开发任务开始时，由独立子代理检查上一轮提交的结果和报告，发现问题在当轮修复。覆盖范围及限制见 [云端验证说明](docs/CLOUD-VALIDATION.md)。

## 云端构建

推送到 `main` 即会自动运行 `build-apk.yml`，产出可直接安装的 APK 并附上构建产物。要打签名 Release 包，在仓库 Secrets 里配置 `LUCENT_KEYSTORE_BASE64` 与 `LUCENT_KEYSTORE_PASSWORD`。
