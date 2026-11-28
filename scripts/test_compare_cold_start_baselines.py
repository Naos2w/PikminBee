import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from cold_start_baseline import main as capture_main, summarise
from compare_cold_start_baselines import compare, main, read_report


PACKAGE = "com.naos.pikminbee"


def report(variant, samples=None):
    return {"metric": "am_start_total_time_ms", "percentile_method": "nearest_rank",
            "package": PACKAGE, "source_sha": "a" * 40, "variant": variant,
            "environment": "synthetic", "abi": "arm64-v8a",
            "apk_sha256": ("b" if variant == "baseline" else "c") * 64,
            **summarise(samples if samples is not None else list(range(100, 120)))}


class ComparisonTest(unittest.TestCase):
    def test_deltas_and_no_acceptance_claim(self):
        result = compare(report("baseline"), report("optimized", list(range(80, 100))))
        self.assertEqual(result["comparison"]["median_ms"],
                         {"baseline": 109.5, "optimized": 89.5,
                          "delta_ms": -20, "delta_percent": -18.265})
        self.assertEqual(result["comparison"]["p95_ms"]["delta_ms"], -20)
        self.assertEqual(result["environment"], "synthetic")
        for field in ("device_conditions_verified", "apk_provenance_verified", "acceptance_verified"):
            self.assertIs(result[field], False)

    def test_regression_keeps_positive_sign(self):
        result = compare(report("baseline", [100] * 20), report("optimized", [125] * 20))
        self.assertEqual(result["comparison"]["median_ms"]["delta_percent"], 25)

    def test_zero_delta_and_unequal_sample_counts(self):
        result = compare(report("baseline", [100] * 20), report("optimized", [100] * 21))
        self.assertEqual((result["baseline_count"], result["optimized_count"]), (20, 21))
        self.assertEqual(result["comparison"]["p95_ms"]["delta_percent"], 0)

    def test_mismatched_metadata_rejected(self):
        for field, value in (("source_sha", "b" * 40), ("abi", "x86_64"),
                             ("environment", "device"), ("package", PACKAGE + ".uat"),
                             ("metric", "other"), ("percentile_method", "other")):
            with self.subTest(field=field):
                modified = report("optimized")
                modified[field] = value
                with self.assertRaisesRegex(ValueError, field):
                    compare(report("baseline"), modified)

    def test_missing_or_invalid_apk_hash_rejected(self):
        for value in (None, "x" * 64, "a" * 63, True):
            with self.subTest(value=value):
                modified = report("optimized")
                modified["apk_sha256"] = value
                with self.assertRaisesRegex(ValueError, "apk_sha256"):
                    compare(report("baseline"), modified)

    def test_identical_apk_hash_rejected(self):
        modified = report("optimized")
        modified["apk_sha256"] = report("baseline")["apk_sha256"]
        with self.assertRaisesRegex(ValueError, "identical APK"):
            compare(report("baseline"), modified)

    def test_distinct_apk_hashes_retained_without_provenance_claim(self):
        result = compare(report("baseline"), report("optimized"))
        self.assertEqual(result["baseline_apk_sha256"], "b" * 64)
        self.assertEqual(result["optimized_apk_sha256"], "c" * 64)
        self.assertIs(result["apk_provenance_verified"], False)

    def test_swapped_variants_rejected(self):
        with self.assertRaisesRegex(ValueError, "baseline"):
            compare(report("optimized"), report("baseline"))

    def test_short_samples_rejected_either_side(self):
        for left, right in ((19, 20), (20, 19)):
            with self.subTest(left=left, right=right), self.assertRaisesRegex(ValueError, "at least 20"):
                compare(report("baseline", [100] * left), report("optimized", [100] * right))

    def test_tampered_statistics_rejected(self):
        for key, value in (("median_ms", 1), ("p95_ms", 1), ("count", 21),
                           ("min_ms", True), ("minimum_20_samples_met", 1)):
            with self.subTest(key=key):
                modified = report("optimized")
                modified[key] = value
                with self.assertRaisesRegex(ValueError, key):
                    compare(report("baseline"), modified)

    def test_missing_and_invalid_samples(self):
        for samples in (None, [], [True] * 20, [0] * 20, [1.5] * 20, "100"):
            with self.subTest(samples=samples), self.assertRaises(ValueError):
                modified = report("optimized")
                modified["samples_ms"] = samples
                compare(report("baseline"), modified)

    def test_non_object_or_malformed_metadata(self):
        for modified in ([], None, {**report("optimized"), "abi": []},
                         {**report("optimized"), "source_sha": 3}):
            with self.subTest(modified=modified), self.assertRaises(ValueError):
                compare(report("baseline"), modified)

    def test_duplicate_keys_and_nonfinite_json_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            for data in ('{"count":20,"count":19}', '{"median_ms":NaN}', '{"median_ms":Infinity}'):
                path.write_text(data, encoding="utf-8")
                with self.subTest(data=data), self.assertRaises(ValueError):
                    read_report(path)

    def test_real_capture_parser_to_comparator_pipeline(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = []
            for variant, initial in (("baseline", 100), ("optimized", 80)):
                paths = []
                for i in range(20):
                    path = root / f"{variant}{i}.txt"
                    path.write_text(f"Status: ok\nLaunchState: COLD\nActivity: {PACKAGE}/.MainActivity\nTotalTime: {initial+i}\n", encoding="utf-8")
                    paths.append(str(path))
                out = io.StringIO()
                with contextlib.redirect_stdout(out):
                    status = capture_main(["--package", PACKAGE, "--source-sha", "a" * 40,
                                           "--variant", variant, "--environment", "synthetic",
                                           "--apk-sha256", ("b" if variant == "baseline" else "c") * 64,
                                           "--abi", "arm64-v8a", *paths])
                self.assertEqual(status, 0)
                target = root / f"{variant}.json"
                target.write_text(out.getvalue(), encoding="utf-8")
                reports.append(str(target))
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                status = main(reports)
            self.assertEqual(status, 0)
            self.assertEqual(json.loads(out.getvalue())["comparison"]["median_ms"]["delta_ms"], -20)

    def test_cli_errors_have_no_json_stdout(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "invalid.json"
            path.write_text("{}", encoding="utf-8")
            out, err = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                status = main([str(path), str(path)])
            self.assertEqual(status, 1)
            self.assertEqual(out.getvalue(), "")
            self.assertIn("expected baseline", err.getvalue())


if __name__ == "__main__":
    unittest.main()
