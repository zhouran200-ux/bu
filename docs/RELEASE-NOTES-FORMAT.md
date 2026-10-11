# Release notes: the house format

Every release body follows the same shape, so the languages sit in the same place every time.

```
# <English subtitle, without the "Lucent x.y.z —" prefix>

<English notes, roughly 500 words, British English, dry, and never negative>

---

<details>
<summary>简体中文</summary>

# <Chinese title>

<Chinese notes>

</details>

---

**Build info**

- Android APK: <size> MiB
- Built from commit `<sha>`
- Build log: <run url>
```

The rules the checker enforces:

- One first-level title at the very top, and it is the subtitle alone, not the whole release name.
- One `<details>` block summarised `简体中文`, opening with its own `# ` title. Never one block with `###` sections inside.
- A `---` rule before every block, and one closing the English notes. The release workflow appends the
  closing rule with the build info block, so notes handed to it carry one rule and the published
  body carries two.
- The English notes stay between 300 and 700 words.
- If a build info block is present it carries the APK size and the commit.

`python3 tools/release_notes_check.py <file>` checks a set of notes; the release workflow runs it on
the notes it is given, and refuses to build a release whose notes do not match.

---

The example below is the shape of the shipped 3.1.2 body, with the build numbers left as placeholders.

# The Notebook Goes Native, and Lighter

Lucent 3.1.2 is a clean Android release: one `app/` module holding every screen, one `baselineprofile/` module that only warms the first frame, and a single interface that speaks careful Simplified Chinese throughout. What follows is what the release does on the phone.

**A knowledge base that is ready when you open the notebook.** Each notebook keeps one local document as its source, and the assistant reads every note inside that notebook as living context. Nothing is indexed in advance; edit a note and the next answer already knows. A source may be `.txt`, `.doc` or `.docx` — parsed into Markdown on the device, the old binary Word format included, written with Lucent's own reader so nothing is uploaded and no office library is asked to help.

**The models stay yours.** Bring your own key: ChatGPT, Gemini, Claude, DeepSeek and Kimi arrive as one-tap presets, and any compatible endpoint can be typed in by hand. Profiles are saved as a list, reordered by long press and drag, refreshed for their model names and tested for connectivity from the settings page itself. A custom User-Agent, with a SillyTavern disguise among the presets, rides on every request.

**Thinking you can watch.** Reasoning models stream their chain of thought into a lane that unfolds as it arrives and folds away when you want the prose. Every notebook keeps its own reasoning level, its own number of history turns and its own prompt, so changing notebooks changes the whole character of the conversation.

**Quiet, and quick.** Gradients drift behind frosted Haze panels; light, dark, system and a wide family of Monet palettes are joined by wallpaper-derived colour on Android 12 and later. Every screen scrolls on its own, remembers its position, and keeps the capsule navigation to the root pages. The APK carries arm64-v8a and x86_64 — the silicon in a modern phone and in the emulator.

Push to `main` and `build-apk.yml` returns a signed release APK; `release.yml` drafts the release and checks these notes on the way out.

---

<details>
<summary>简体中文</summary>

# 单模块安卓，更轻，也更快

Lucent 3.1.2 是一份干净的 Android 版本：所有界面收在 `app/` 一个模块里，`baselineprofile/` 只负责把首屏预热好，界面语言仍是一丝不苟的简体中文，且只此一种。下面写的是它在手机上真正做的事。

**翻开笔记本，知识库就在。** 每本笔记本保存一份本地文档作为来源，助手实时取用该笔记本内的全部笔记作为上下文，无需预先建立索引；笔记一改，下一句回答即刻知道。来源可以是 `.txt`、`.doc` 或 `.docx`，由 Lucent 自己的解析器在设备上读成 Markdown——老二进制 Word 也在内，不依赖任何 Office 库，不上传，不经过转换服务。

**模型由你决定。** 自带服务商：ChatGPT、Claude、Gemini、DeepSeek、Kimi 都是一键套用的预设，任意兼容地址也能手填。方案成组保存，长按拖动排序，模型列表就地获取，连通性在设置里直接测试。自定义 User-Agent 随每次请求一同发出，预设里有 SillyTavern 伪装一档。

**思考过程看得见。** 推理模型的思考链会实时流式展开，想读就摊开，想安静就折起。思考强度、携带的历史轮数与自定义提示词都按笔记本各自保存，换一本即刻换一套，绝不串台。

**安静，也快。** 渐变色在毛玻璃高斯模糊面板后方漂移；浅色、深色、跟随系统之外，还有一整排 Monet 配色，Android 12+ 可跟随壁纸取色。每面各自滚动、各自记住位置，底部胶囊导航只在根级出现。安装包只带 arm64-v8a 与 x86_64 两种架构——真机与模拟器所用的那两种。

推送到 `main`，`build-apk.yml` 就交出签好名的 Release APK；`release.yml` 起草发布单，并在这段说明不合格式时拒绝发布。

</details>

---

**Build info**

- Android APK: `<size>` MiB
- Built from commit `<sha>`
- Build log: `<run url>`
