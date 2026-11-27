"""Unit tests for DEV patches, UAT patch promotion and explicit UAT milestones."""

import unittest

from bump_version import Version, update, updated_versions, versions


BASE = (
    "org.gradle.caching=true\n"
    "pikminbeeUatVersion=0.1.1\n"
    "pikminbeeDevVersion=0.1.2\n"
    "kotlin.code.style=official\n"
)


class VersioningTests(unittest.TestCase):
    def test_first_uat_and_first_dev_are_distinct(self):
        self.assertEqual((Version(0, 1, 2), Version(0, 1, 1)), versions(BASE))

    def test_dev_bumps_only_third_number(self):
        bumped = update(BASE, "dev")
        self.assertEqual((Version(0, 1, 3), Version(0, 1, 1)), versions(bumped))
        self.assertIn("org.gradle.caching=true", bumped)
        self.assertIn("kotlin.code.style=official", bumped)

    def test_uat_promotes_dev_patch_and_advances_dev(self):
        self.assertEqual((Version(0, 1, 3), Version(0, 1, 2)), versions(update(BASE, "uat")))
        self.assertEqual((Version(0, 1, 4), Version(0, 1, 3)), versions(update(update(BASE, "dev"), "uat")))

    def test_repeated_uat_bumps_progress_patches(self):
        self.assertEqual(
            (Version(0, 1, 4), Version(0, 1, 3)),
            versions(update(update(BASE, "uat"), "uat")),
        )

    def test_explicit_minor_milestone_resets_patches(self):
        bumped = update(update(BASE, "uat"), "uat-minor")
        self.assertEqual((Version(0, 2, 2), Version(0, 2, 1)), versions(bumped))
        self.assertEqual((Version(0, 2, 3), Version(0, 2, 2)), versions(update(bumped, "uat")))

    def test_invalid_version_sequences_are_rejected(self):
        for invalid in (
            BASE.replace("0.1.2", "0.1.1"),
            BASE.replace("0.1.2", "0.2.2"),
            BASE.replace("pikminbeeUatVersion=0.1.1", "pikminbeeUatVersion=0.1.2"),
            BASE.replace("pikminbeeDevVersion=0.1.2", "pikminbeeDevVersion=0.1.02"),
            BASE.replace("pikminbeeUatVersion=0.1.1\n", ""),
        ):
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    versions(invalid)

    def test_component_overflow_is_rejected(self):
        with self.assertRaises(ValueError):
            updated_versions(Version(0, 1, 999), Version(0, 1, 1), "dev")
        with self.assertRaises(ValueError):
            updated_versions(Version(0, 1, 999), Version(0, 1, 998), "uat")
        with self.assertRaises(ValueError):
            updated_versions(Version(0, 999, 2), Version(0, 999, 1), "uat-minor")


if __name__ == "__main__":
    unittest.main()
