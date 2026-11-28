import tempfile
from pathlib import Path
import unittest
import zipfile

from apk_size_audit import audit
from apk_size_compare import compare

SHA = "a" * 40


class ApkSizeCompareTest(unittest.TestCase):
    def fixture(self, directory, name, dex_bytes, abi="arm64-v8a", sha=SHA):
        apk = Path(directory) / name
        with zipfile.ZipFile(apk, "w", zipfile.ZIP_STORED) as archive:
            archive.writestr("lib/" + abi + "/libtest.so", b"native")
            archive.writestr("classes.dex", b"d" * dex_bytes)
            archive.writestr("assets/lan/controller.js", b"web")
        return audit(apk, sha, abi)

    def test_reconciles_real_zip_sizes_and_category_deltas(self):
        with tempfile.TemporaryDirectory() as directory:
            baseline = self.fixture(directory, "base.apk", 1000)
            candidate = self.fixture(directory, "candidate.apk", 500)
            result = compare(baseline, candidate)
            self.assertEqual(-500, result["delta_bytes"])
            self.assertEqual(-500, result["category_delta_bytes"]["dex"])
            self.assertEqual(0, result["category_delta_bytes"]["native"])
            self.assertEqual(0, result["category_delta_bytes"]["web"])
            self.assertEqual(0, result["archive_overhead_delta_bytes"])
            self.assertGreater(result["reduction_percent"], 0)
            self.assertEqual(SHA, result["source_sha"])

    def test_rejects_different_source_abi_and_identical_apks(self):
        with tempfile.TemporaryDirectory() as directory:
            baseline = self.fixture(directory, "base.apk", 1000)
            for candidate in (
                self.fixture(directory, "other-source.apk", 500, sha="b" * 40),
                self.fixture(directory, "other-abi.apk", 500, abi="x86_64"),
                baseline,
            ):
                with self.subTest(candidate=candidate["apk"]):
                    with self.assertRaises(ValueError):
                        compare(baseline, candidate)

    def test_rejects_missing_or_short_source_sha(self):
        with tempfile.TemporaryDirectory() as directory:
            for sha in ("", "abc123"):
                baseline = self.fixture(directory, "base.apk", 1000, sha=sha)
                candidate = self.fixture(directory, "candidate.apk", 500, sha=sha)
                with self.assertRaises(ValueError):
                    compare(baseline, candidate)

    def test_warns_about_large_packaging_overhead(self):
        with tempfile.TemporaryDirectory() as directory:
            baseline = self.fixture(directory, "base.apk", 1000)
            candidate = self.fixture(directory, "candidate.apk", 500)
            # A valid ZIP may have unused bytes before its first entry.
            path = Path(directory) / "candidate.apk"
            path.write_bytes(b"\x00" * (2 * 1024 * 1024) + path.read_bytes())
            candidate = audit(path, SHA, "arm64-v8a")
            result = compare(baseline, candidate)
            self.assertEqual(1, len(result["packaging_warnings"]))
            self.assertIn("candidate:", result["packaging_warnings"][0])
            self.assertGreater(result["delta_bytes"], 0)
            self.assertEqual([], compare(baseline, self.fixture(
                directory, "clean.apk", 500))["packaging_warnings"])

    def test_growth_is_reported_as_negative_reduction(self):
        with tempfile.TemporaryDirectory() as directory:
            baseline = self.fixture(directory, "base.apk", 500)
            candidate = self.fixture(directory, "candidate.apk", 1000)
            self.assertLess(compare(baseline, candidate)["reduction_percent"], 0)


if __name__ == "__main__":
    unittest.main()
