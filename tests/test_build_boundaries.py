"""Guard release boundaries without distributing any vendor source or APK."""
import argparse
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("lensfix_build", Path(__file__).parents[1] / "scripts/build.py")
BUILD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILD)


class BuildBoundaries(unittest.TestCase):
    def test_unknown_app_stops_before_any_tool_or_output_is_accessed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "base.apk").write_bytes(b"An unsupported app must never reach the patcher")
            # No other attributes exist: touching tools/work/output before checking input fails this test.
            args = argparse.Namespace(input=root)
            with self.assertRaisesRegex(ValueError, "Unsupported or altered input: base.apk"):
                BUILD.build(args)
            self.assertEqual(["base.apk"], [p.name for p in root.iterdir()])

    def test_identity_rewrite_preserves_java_class_names_and_binary_size(self):
        for encoding, end in [("utf-8", b"\0"), ("utf-16le", b"\0\0")]:
            strings = ["com.lensmoo.app", "com.lensmoo.app.LensMooApp", "com.lensmoo.app.provider"]
            before = b"".join(value.encode(encoding) + end for value in strings)
            after = BUILD.binary_string(before, "com.lensmoo.app", "com.lensmoo.fix")
            expected = b"".join(value.encode(encoding) + end for value in ["com.lensmoo.fix", *strings[1:]])
            self.assertEqual(expected, after)
            self.assertEqual(len(before), len(after))

    def test_binary_rewrite_rejects_length_changes(self):
        with self.assertRaises(ValueError):
            BUILD.binary_string(b"com.lensmoo.app\0", "com.lensmoo.app", "com.example.longer")

    def test_patch_refuses_unknown_or_duplicate_callback_patterns(self):
        for source in ["", "different vendor implementation", BUILD.OLD_CALL + "\n" + BUILD.OLD_CALL]:
            with self.subTest(source=source), self.assertRaises(ValueError):
                BUILD.patch_dex(source)


if __name__ == "__main__":
    unittest.main()
