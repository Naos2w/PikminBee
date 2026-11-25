"""Production metadata and signature gate tests (synthetic certificates only)."""

import unittest
from bump_version import Version
from production_release import (
    PACKAGE, UAT_CERTIFICATE, validate_badging, validate_certificate, validate_metadata, version_code,
)


class ProductionReleaseTests(unittest.TestCase):
    version = Version(1, 0, 0)
    metadata = {"applicationId": PACKAGE, "elements": [
        {"versionName": "1.0.0", "versionCode": 1000000, "outputFile": "app-release.apk"}
    ]}
    badging = "package: name='com.naos.pikminbee.prod' versionCode='1000000' versionName='1.0.0'\n"

    def test_exact_production_identity_and_monotonic_version_code(self):
        self.assertEqual("app-release.apk", validate_metadata(self.metadata, self.version))
        self.assertEqual(1000000, version_code(self.version))
        self.assertLess(version_code(self.version), version_code(Version(1, 0, 1)))
        self.assertLess(version_code(Version(1, 0, 999)), version_code(Version(1, 1, 0)))
        validate_badging(self.badging, self.version)

    def test_rejects_wrong_identity_code_version_or_filename(self):
        element = self.metadata["elements"][0]
        for invalid in (
            {**self.metadata, "applicationId": "com.naos.pikminbee.uat"},
            {**self.metadata, "elements": []},
            {**self.metadata, "elements": [{**element, "versionCode": 1232}]},
            {**self.metadata, "elements": [{**element, "versionName": "1.0.0-dev"}]},
            {**self.metadata, "elements": [{**element, "outputFile": "../other.apk"}]},
        ):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                validate_metadata(invalid, self.version)
        for invalid in (self.badging + "application-debuggable\n",
                        self.badging.replace(".prod", ".uat"),
                        self.badging.replace("1000000", "1232")):
            with self.assertRaises(ValueError):
                validate_badging(invalid, self.version)

    def test_requires_independent_pinned_certificate(self):
        pin = "a" * 64
        report = f"V2 Signer: certificate SHA-256 digest: {pin}\n"
        self.assertEqual(pin, validate_certificate(report, pin))
        for expected in ("", "bad", "b" * 64, UAT_CERTIFICATE):
            with self.assertRaises(ValueError):
                validate_certificate(report, expected)
        with self.assertRaises(ValueError):
            validate_certificate(report + "Signer #2 certificate SHA-256 digest: " + "b" * 64, pin)
        with self.assertRaises(ValueError):
            validate_certificate("no certificate", pin)


if __name__ == "__main__":
    unittest.main()
