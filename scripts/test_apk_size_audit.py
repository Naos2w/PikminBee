import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile

from apk_size_audit import audit, SUPPORTED_ABIS


class ApkSizeAuditTest(unittest.TestCase):
    def make_fixture(self, directory, abis):
        apk = Path(directory) / "test.apk"
        with zipfile.ZipFile(apk, "w", zipfile.ZIP_DEFLATED) as archive:
            for abi in abis:
                archive.writestr("lib/" + abi + "/libtest.so", b"native" * 10)
            archive.writestr("classes.dex", b"dex" * 100)
            archive.writestr("classes2.dex", b"more dex" * 10)
            archive.writestr("assets/lan/controller.js", b"web" * 100)
            archive.writestr("res/drawable/test.xml", b"<xml/>")
            archive.writestr("AndroidManifest.xml", b"manifest")
        return apk

    def test_reports_actual_apk_bytes_and_hash_without_double_counting(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = self.make_fixture(directory, {"arm64-v8a"})
            report = audit(apk, "synthetic", "arm64-v8a")
            self.assertEqual(apk.stat().st_size, report["apk_bytes"])
            self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), report["apk_sha256"])
            self.assertEqual(["arm64-v8a"], report["abis"])
            self.assertEqual(2, report["dex_count"])
            self.assertEqual(report["abi_bytes"]["arm64-v8a"], report["category_bytes"]["native"])
            self.assertEqual(300, report["category_uncompressed_bytes"]["web"])
            self.assertEqual(report["apk_bytes"], sum(report["category_bytes"].values())
                             + report["archive_overhead_bytes"])

    def test_expected_abi_set_rejects_wrong_or_missing_binaries(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = self.make_fixture(directory, SUPPORTED_ABIS)
            self.assertEqual(SUPPORTED_ABIS, set(audit(apk, expected_abi="universal")["abis"]))
            with self.assertRaises(ValueError):
                audit(apk, expected_abi="arm64-v8a")
            apk = self.make_fixture(directory, {"x86_64"})
            with self.assertRaises(ValueError):
                audit(apk, expected_abi="universal")
            self.assertEqual(["x86_64"], audit(apk, expected_abi="x86_64")["abis"])


if __name__ == "__main__":
    unittest.main()
