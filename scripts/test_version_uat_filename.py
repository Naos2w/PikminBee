"""UAT artifact filename must come from the signed build's validated metadata."""

import unittest

from uat_release_filename import verified_uat_version


class UatFileNameTests(unittest.TestCase):
    def test_semantic_version_generates_safe_filename_suffix(self):
        metadata = {"applicationId": "com.naos.pikminbee.uat",
                    "elements": [{"versionName": "0.16.1", "versionCode": 1136}]}
        version = verified_uat_version(metadata)
        self.assertEqual("PikminBee-UAT-0.16.1.apk", f"PikminBee-UAT-{version}.apk")
        self.assertEqual("PikminBee-UAT-0.16.1-signed", f"PikminBee-UAT-{version}-signed")

    def test_patch_release_filename(self):
        metadata = {"applicationId": "com.naos.pikminbee.uat",
                    "elements": [{"versionName": "0.33.2", "versionCode": 1285}]}
        version = verified_uat_version(metadata)
        self.assertEqual("PikminBee-UAT-0.33.2.apk", f"PikminBee-UAT-{version}.apk")

    def test_rejects_other_package_missing_output_or_unsafe_versions(self):
        good = {"applicationId": "com.naos.pikminbee.uat",
                "elements": [{"versionName": "0.16.1"}]}
        cases = [
            {**good, "applicationId": "com.naos.pikminbee"},
            {**good, "elements": []},
            {**good, "elements": [{"versionName": "0.16.1"}, {"versionName": "0.16.1"}]},
            {**good, "elements": [{"versionName": "0.16.1/../../evil"}]},
            {**good, "elements": [{"versionName": "0.16.1000"}]},
            {**good, "elements": [{"versionName": "0.16.1-dev"}]},
            {**good, "elements": [{"versionName": "0.016.1"}]},
        ]
        for item in cases:
            with self.subTest(item=item), self.assertRaises(ValueError):
                verified_uat_version(item)


if __name__ == "__main__":
    unittest.main()
