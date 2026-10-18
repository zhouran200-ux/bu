import base64
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/build-apk.yml"
BASH = shutil.which("bash")
if not BASH and os.name == "nt":
    candidate = Path("C:/Program Files/Git/bin/bash.exe")
    if candidate.exists():
        BASH = str(candidate)


def shell_path(path):
    value = Path(path).resolve().as_posix()
    if os.name == "nt":
        return "/" + value[0].lower() + value[2:]
    return value


@unittest.skipUnless(BASH, "Bash is required to execute the CI signing script")
class SigningContractTest(unittest.TestCase):
    def run_signing(self, required="false", store="", password="", alias="", key_password=""):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        section = workflow.split("      - name: Resolve signing key", 1)[1].split("      - name: Set up JDK 17", 1)[0]
        script = textwrap.dedent(section.split("        run: |", 1)[1])
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "debug.keystore").write_bytes(b"preview-test-key")
            script_path = root / "sign.sh"
            script_path.write_text(script, encoding="utf-8")
            env = dict(os.environ, KEYSTORE_B64=store, KEYSTORE_PW=password,
                       KEY_ALIAS=alias, KEY_PASSWORD=key_password,
                       REQUIRE_RELEASE_SIGNING=required, RUNNER_TEMP=shell_path(root),
                       GITHUB_WORKSPACE=shell_path(root),
                       GITHUB_OUTPUT=shell_path(root / "output"),
                       GITHUB_ENV=shell_path(root / "env"))
            result = subprocess.run([BASH, shell_path(script_path)], env=env, capture_output=True, text=True, encoding="utf-8")
            output = (root / "output").read_text() if (root / "output").exists() else ""
            settings = (root / "env").read_text() if (root / "env").exists() else ""
            return result, output, settings

    def test_preview_keeps_existing_debug_signing(self):
        result, output, _ = self.run_signing()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("mode=debug-signed", output)
        self.assertIn("::warning::", result.stdout)

    def test_formal_release_refuses_missing_key(self):
        result, output, _ = self.run_signing(required="true")
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("mode=debug-signed", output)

    def test_partial_configuration_never_falls_back(self):
        for store, password in (("a2V5", ""), ("", "password")):
            result, output, _ = self.run_signing(store=store, password=password)
            self.assertNotEqual(0, result.returncode)
            self.assertNotIn("mode=debug-signed", output)

    def test_release_supports_custom_alias_and_key_password(self):
        result, output, settings = self.run_signing(
            required="true", store=base64.b64encode(b"test-key").decode(),
            password="store-test", alias="custom", key_password="key-test")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("mode=release", output)
        self.assertIn("alias=custom", output)
        self.assertIn("LUCENT_KEY_PASSWORD=key-test", settings)

    def test_release_defaults_alias_and_key_password(self):
        result, output, settings = self.run_signing(store="a2V5", password="store-test")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("alias=lucent", output)
        self.assertIn("LUCENT_KEY_PASSWORD=store-test", settings)

    def test_formal_workflow_requires_release_signing(self):
        text = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertIn("require_release_signing: true", text)
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("if: ${{ !inputs.require_release_signing }}", workflow)
