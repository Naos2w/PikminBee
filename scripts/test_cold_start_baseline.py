import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from cold_start_baseline import main, parse_sample, summarise

PACKAGE = "com.naos.pikminbee.uat"


def capture(ms=100, state="COLD", package=PACKAGE):
    return f"Status: ok\nLaunchState: {state}\nActivity: {package}/.MainActivity\nTotalTime: {ms}\nWaitTime: 999\nComplete\n"


class ColdStartBaselineTest(unittest.TestCase):
    def test_parses_total_time_not_wait_time(self):
        self.assertEqual(parse_sample(capture(123), PACKAGE), 123)

    def test_rejects_non_cold_and_unknown(self):
        for state in ["WARM", "HOT", "UNKNOWN", ""]:
            with self.subTest(state=state), self.assertRaises(ValueError):
                parse_sample(capture(state=state), PACKAGE)

    def test_rejects_invalid_capture(self):
        for value in [capture().replace("Status: ok", "Status: error"),
                      capture().replace("LaunchState: COLD\n", ""),
                      capture(package="com.example.other"),
                      capture(0), capture(-1), capture("NaN"),
                      capture() + "TotalTime: 200\n"]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                parse_sample(value, PACKAGE)

    def test_nearest_rank_and_median(self):
        result = summarise(list(range(20, 0, -1)))
        self.assertEqual(result["median_ms"], 10.5)
        self.assertEqual(result["p95_ms"], 19)
        self.assertTrue(result["minimum_20_samples_met"])

    def test_small_and_invalid_sets(self):
        self.assertFalse(summarise([100])["minimum_20_samples_met"])
        for samples in [[], [0], [True], [1.5]]:
            with self.subTest(samples=samples), self.assertRaises(ValueError):
                summarise(samples)

    def test_cli_metadata_and_reject_duplicate(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "sample.txt"
            file.write_text(capture(), encoding="utf-8")
            argv = ["--package", PACKAGE, "--source-sha", "a" * 40,
                    "--variant", "baseline", "--environment", "synthetic",
                    "--abi", "arm64-v8a"]
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(main(argv + [str(file)]), 0)
            report = json.loads(out.getvalue())
            self.assertEqual(report["environment"], "synthetic")
            self.assertEqual(report["samples_ms"], [100])
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(main(argv + [str(file), str(file)]), 1)


if __name__ == "__main__":
    unittest.main()
