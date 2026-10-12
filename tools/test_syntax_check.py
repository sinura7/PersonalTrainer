"""Exercise the real parse launcher against compiler/startup outcomes."""
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest


class SyntaxLauncherTest(unittest.TestCase):
    def invoke(self, status, stdout="", stderr=""):
        script = Path(__file__).resolve().with_name("syntax-check.sh")
        shell = shutil.which("sh")
        if shell is None and os.name == "nt":
            shell = str(Path(os.environ["ProgramFiles"]) / "Git/usr/bin/sh.exe")
        self.assertIsNotNone(shell, "The verification lane requires a POSIX shell")
        with tempfile.TemporaryDirectory(prefix="temper-syntax-test-") as directory:
            root = Path(directory)
            binary = root / "bin"
            binary.mkdir()
            java = binary / "java"
            java.write_text(
                "#!/bin/sh\n"
                "case \"$*\" in *'-Xms32m -Xmx512m'*) ;; *) exit 95 ;; esac\n"
                f"printf '%s' {shlex.quote(stdout)}\n"
                f"printf '%s' {shlex.quote(stderr)} >&2\n"
                f"exit {status}\n",
                encoding="utf-8", newline="\n",
            )
            java.chmod(0o755)
            jars = root / "jars"
            jars.mkdir()
            (jars / "kotlin-compiler-embeddable-2.0.21.jar").touch()
            source = root / "src"
            source.mkdir()
            catalog = root / "versions.toml"
            catalog.write_text('[versions]\nkotlin = "2.0.21"\n', encoding="utf-8")
            env = dict(os.environ)
            env.update(PATH=os.pathsep.join((str(binary), str(Path(shell).parent), env["PATH"])),
                       GRADLE_USER_HOME=str(root / "empty-gradle"),
                       PT_JARS=str(jars), PT_KOTLIN="2.0.21")
            return subprocess.run(
                [shell, str(script), str(source), str(catalog)],
                cwd=root, env=env, text=True, capture_output=True, timeout=15,
            )

    def test_parsed_clean(self):
        result = self.invoke(0)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("NO SYNTAX ERRORS", result.stdout)

    def test_expected_unresolved_source_references(self):
        result = self.invoke(1, stderr="src/Test.kt:1:8: error: unresolved reference 'android'.\n")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("NO SYNTAX ERRORS", result.stdout)

    def test_parse_error_remains_a_finding(self):
        result = self.invoke(1, stderr="src/Test.kt:3:2: error: Syntax error: Expecting '}'.\n")
        self.assertEqual(result.returncode, 0)
        self.assertNotIn("NO SYNTAX ERRORS", result.stdout)
        self.assertIn("Expecting", result.stdout)

    def test_startup_exit_one_without_diagnostics_fails(self):
        result = self.invoke(1)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("failed without source diagnostics", result.stdout)

    def test_native_memory_failure_on_stdout_fails(self):
        result = self.invoke(1, stdout="# There is insufficient memory for the Java Runtime Environment to continue.\n")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("DID NOT RUN", result.stdout)

    def test_vm_failure_on_stderr_fails(self):
        result = self.invoke(1, stderr="Error occurred during initialization of VM\nCould not reserve enough space\n")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("NO SYNTAX ERRORS", result.stdout)

    def test_fatal_after_source_diagnostics_still_fails(self):
        result = self.invoke(1, stdout="src/Test.kt:1:8: error: unresolved reference 'android'.\n# A fatal error has been detected\n")
        self.assertNotEqual(result.returncode, 0)

    def test_other_exit_codes_fail(self):
        for status in (2, 127):
            with self.subTest(status=status):
                result = self.invoke(status)
                self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
