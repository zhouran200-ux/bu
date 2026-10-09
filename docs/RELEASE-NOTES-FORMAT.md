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

The example below is the shipped 3.0.0 body, kept as a reference.

# Third Hand: The Notebook Becomes a Workshop

Lucent 3.0.0 turns the assistant from a well-mannered conversationalist into something with a workshop, a key to it, and a written record of everything it does in there. Nothing about the notes and tasks you already keep has changed; there is simply a great deal more behind the door now.

**A toolkit, not a toybox.** The assistant gets a workspace folder of your choosing and real tools: list, search, read, write, edit with a proper diff, copy, move, delete, zip, and a snapshot of every file before it is touched, so any mistake can be walked back. Long jobs become background tasks you can poll and stop, and commands run with a working directory and a timeout.

**Documents, made properly.** Word, Excel and PowerPoint files are now created and edited on your device, in formats written by Lucent itself — no office library, no upload, no conversion service. Word documents get styles, tables, images, headers, footers, page numbers and a table of contents; spreadsheets get formulas, formats, merges, frozen panes, filters, conditional colours and charts; presentations get masters, layouts, themes, speaker notes and editable charts. PDFs can be read, searched, split, merged and drawn as pictures — and a rendered page is something the assistant can look at, which is how it notices that slide seven has spilled its text.

**Code, kept honest.** Git has a full set of guarded tools — status, diff, log, blame, branch, commit, stash, merge, rebase, patch, fetch, pull and push — and the dangerous corners are refused rather than regretted. GitHub follows: repositories, files, issues, pull requests, diffs, branches, releases, code search, workflow runs and CI logs, so a failing build can be read and fixed rather than merely described.

**A workshop with a rulebook.** Permissions are their own page: read, write, delete, commands, network, Git, GitHub, browsing, the world outside the workspace and the device itself, each set to allow, ask or block. Deleting and installing ask first by default. Every call lands in an activity log with what was asked, what happened and how long it took, and the log is yours to read or clear.

**Heavy things stay outside.** The installer is not asked to carry a Linux userland, Python, LibreOffice, Node.js, a browser engine, OCR or media tools. They are plugins, listed with their size and licence, downloaded only when you want them, from the project's own servers or a fast mirror, whichever answers first. The assistant can install them too, once it has told you how big they are.

**Sub-agents, for the heavy days.** A switch in Personalisation lets the assistant call for helpers: independent tasks go to sub-agents that work in the background with their own context and report back, so a long investigation no longer eats the conversation you are having.

**Three kinds of memory, and a plan.** This conversation, this project and you are remembered separately. Anything with more than two steps gets a plan you can watch, and skills are written down, so your conventions are learned once.

**On the phone, the workshop reaches the device.** Read the screen, tap, type, swipe, take a screenshot, list and open apps, read notifications, use the clipboard, the flashlight and the sensors. Termux handles real command-line tools, and MCP lets you bolt on anything else.

The Android download is now one universal build for arm64-v8a, armeabi-v7a and x86_64.

---

<details>
<summary>简体中文</summary>

# 第三只手：笔记本变成了一间工作室

Lucent 3.0.0 把助手从一位彬彬有礼的聊天对象，变成了一间工作室的主人：有钥匙，也有把里面每件事都记下来的账本。你原有的笔记与任务没有任何变化，只是那扇门后面丰富了许多。

**工具箱，不是玩具箱。** 助手得到你指定的工作区文件夹和一套真正的工具：列目录、搜索、读取、写入、带差异对比的编辑、复制、移动、删除、打包，以及每次改动前的自动快照，出错随时退回。耗时的工作变成后台任务，可以随时查看和终止；命令带工作目录、超时和完整输出。

**文档，正经地做。** Word、Excel、PowerPoint 现在由 Lucent 本机生成和修改，文件格式由 Lucent 自己写出——不依赖任何 Office 库，不上传，不经过转换服务。Word 有样式、表格、图片、页眉页脚、页码和目录；表格有公式、格式、合并、冻结窗格、筛选、条件颜色和图表；演示文稿有母版、版式、主题、备注和可编辑图表。PDF 可以读取、搜索、拆分、合并并渲染成图片，而渲染出来的页面助手能亲眼看——第七页文字溢出来了，它看得见。

**代码，规规矩矩。** Git 工具齐备：状态、差异、日志、追溯、分支、提交、暂存、合并、变基、补丁、拉取、推送，危险的动作直接拒绝而不是事后道歉。GitHub 亦然：仓库、文件、议题、拉取请求、差异、分支、发布、代码搜索、流水线与构建日志，失败的构建可以读、可以修。

**有规矩的工作室。** 权限自成一面：读取、写入、删除、命令、网络、Git、GitHub、浏览、工作区之外、以及设备本身，每项都可以设为允许、询问或禁止。删除与安装默认先问。每次调用都会写入操作记录，写了什么、结果如何、花了多久，随时可看可清。

**重的东西留在门外。** 安装包不背 Linux 环境、Python、LibreOffice、Node.js、浏览器内核、OCR 和媒体工具。它们是插件，标明体积与许可证，只在你需要时下载，官方地址与国内镜像自动测速取快。助手也可以自己装，前提是先告诉你要下多大。

**子助手，忙的时候用。** 个性化里有一个开关：打开后助手可以召唤帮手，独立的任务交给在后台用自己上下文工作的子助手，完成后汇报，长时间调查不再占用你正在聊的这一段。

**三种记忆，一份计划。** 本次对话、这个项目、以及你本人，分开记住。超过两步的任务会给出可实时查看的计划；技能会被写下来，你的习惯只需要教一次。

**在手机上，工作室延伸到设备。** 读屏、点击、输入、滑动、截图、列出并打开应用、读取通知，以及剪贴板、手电筒和传感器。Termux 负责真正的命令行工具，MCP 还能把别的能力接进来。

安卓版现在是一个通用包，同时支持 arm64-v8a、armeabi-v7a 与 x86_64。

</details>

---

**Build info**

- Android APK: 58.73 MiB
- Windows installer: 165.37 MiB
- Built from commit `ba4dfd072069b4f7d95978063c327da79dead64e`
- Build log: https://github.com/zhouran200-ux/bu/actions/runs/36127855604
