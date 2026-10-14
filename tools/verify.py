import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))

LEVELS = ("quick", "logic", "full")

PY = sys.executable

HYGIENE = [PY, "tools/code_hygiene_check.py"]
VERSION = [PY, "tools/version_check.py"]
SETTINGS = [PY, "tools/settings_cache_check.py"]
SCROLL_SELFTEST = [PY, "tools/compose_scroll_check.py", "--self-test"]
SCROLL = [PY, "tools/compose_scroll_check.py"]
DESIGN = [PY, "tools/design_audit.py"]

GRADLE = "./gradlew" if os.name != "nt" else "gradlew.bat"
UNIT_TESTS = [GRADLE, ":app:testDebugUnitTest", "--console=plain"]
COMPILE_RELEASE = [GRADLE, ":app:compileReleaseKotlin", "--console=plain"]
LINT = [GRADLE, ":app:lintRelease", "--console=plain"]
COMPILE_ANDROIDTEST = [GRADLE, ":app:compileDebugAndroidTestKotlin", "--console=plain"]


def plan(level):
    static = [
        ("代码卫生 (无注释/括号/体积)", HYGIENE),
        ("版本号一致", VERSION),
        ("设置缓存回写一致", SETTINGS),
        ("嵌套滚动自检", SCROLL_SELFTEST),
        ("嵌套滚动扫描", SCROLL),
    ]
    design = [("UI/美术规范评分", DESIGN)]
    if level == "quick":
        return static + design
    if level == "logic":
        return static + [
            ("单元测试", UNIT_TESTS),
            ("release 编译", COMPILE_RELEASE),
        ] + design
    return static + [
        ("单元测试", UNIT_TESTS),
        ("release 编译", COMPILE_RELEASE),
        ("Android lint", LINT),
        ("插桩测试编译", COMPILE_ANDROIDTEST),
    ] + design


def run_step(name, cmd):
    print(f"\n==== {name} ====")
    result = subprocess.run(cmd, cwd=REPO)
    return result.returncode == 0


def main():
    level = "quick"
    for arg in sys.argv[1:]:
        if arg in LEVELS:
            level = arg
    steps = plan(level)
    print(f"验证档位: {level}  (共 {len(steps)} 项)")

    results = []
    for name, cmd in steps:
        ok = run_step(name, cmd)
        results.append((name, ok))

    print("\n================ 验证分项报告 ================")
    hard_fail = False
    for name, ok in results:
        if name == "UI/美术规范评分":
            mark = "参考" if ok else "参考(有告警)"
        else:
            mark = "通过" if ok else "失败"
            if not ok:
                hard_fail = True
        print(f"  [{mark}] {name}")

    print("\n本次仅执行上表列出的检查；未执行或失败的检查不视为通过。")
    print("UI/美术规范为静态评分, 仅供参考, 不作阻断。")
    print("未自动验证 (需真机实测): 动画手感、手势是否跟手、视觉居中与配色观感、长按拖动与边缘返回冲突。")
    print("云端验证: push 到 main 后由 GitHub Actions 执行单测、编译、Lint 和模拟器测试；推送后不等待结果。")

    if hard_fail:
        print("\n结论: 有硬性检查失败, 不应推送。")
        return 1
    print("\n结论: 硬性检查全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
