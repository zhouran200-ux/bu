import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/lucent/app/i18n/I18n.kt"


def main():
    before = TARGET.read_bytes()
    generated = subprocess.run(
        [sys.executable, str(ROOT / "tools/i18n/gen_i18n.py")],
        cwd=str(ROOT),
    )
    if generated.returncode != 0:
        print("::error::i18n generator failed")
        return generated.returncode
    after = TARGET.read_bytes()
    if before != after:
        print("::error::generated I18n.kt differs from the committed file; run tools/i18n/gen_i18n.py and commit it")
        return 1
    print("Generated i18n source matches the committed file.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
