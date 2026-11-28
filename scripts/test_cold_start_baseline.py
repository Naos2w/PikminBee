import contextlib
import io
import json
import os
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

    def run_captures(self, paths):
        argv = ["--package", PACKAGE, "--source-sha", "a" * 40,
                "--variant", "baseline", "--environment", "synthetic",
                "--abi", "arm64-v8a"]
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = main(argv + [str(path) for path in paths])
        return code, out.getvalue(), err.getvalue()

    def test_declared_apk_hash_normalised(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.txt"
            path.write_text(capture(), encoding="utf-8")
            args = ["--package", PACKAGE, "--source-sha", "a" * 40,
                    "--variant", "baseline", "--environment", "synthetic",
                    "--abi", "arm64-v8a", "--apk-sha256", "B" * 64, str(path)]
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(main(args), 0)
            self.assertEqual(json.loads(out.getvalue())["apk_sha256"], "b" * 64)

    def test_bad_apk_hash_rejected(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as ctx:
            main(["--package", PACKAGE, "--source-sha", "a" * 40,
                  "--variant", "baseline", "--environment", "synthetic",
                  "--abi", "arm64-v8a", "--apk-sha256", "z" * 64, "sample.txt"])
        self.assertEqual(ctx.exception.code, 2)

    def test_hardlink_cannot_turn_19_launches_into_20(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / f"sample{i}.txt" for i in range(19)]
            for path in paths:
                path.write_text(capture(), encoding="utf-8")
            alias = Path(directory) / "alias.txt"
            try:
                os.link(paths[0], alias)
            except (OSError, NotImplementedError) as exc:
                self.skipTest(f"hardlinks unavailable: {exc}")
            code, out, err = self.run_captures(paths + [alias])
            self.assertEqual(code, 1)
            self.assertEqual(out, "")
            self.assertIn("duplicate capture file", err)

    def test_independent_identical_captures_count_separately(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / f"sample{i}.txt" for i in range(20)]
            for path in paths:
                path.write_text(capture(), encoding="utf-8")
            code, out, _ = self.run_captures(paths)
            self.assertEqual(code, 0)
            report = json.loads(out)
            self.assertEqual(report["count"], 20)
            self.assertTrue(report["minimum_20_samples_met"])

    def test_reject_non_regular_file(self):
        with tempfile.TemporaryDirectory() as directory:
            code, out, err = self.run_captures([Path(directory)])
            self.assertEqual(code, 1)
            self.assertEqual(out, "")
            self.assertIn("not a regular capture file", err)

    @unittest.skipUnless(hasattr(os, "mkfifo"), "FIFO unavailable")
    def test_fifo_rejected_without_reading(self):
        with tempfile.TemporaryDirectory() as directory:
            fifo = Path(directory) / "capture.pipe"
            os.mkfifo(fifo)
            code, out, err = self.run_captures([fifo])
            self.assertEqual(code, 1)
            self.assertEqual(out, "")
            self.assertIn("not a regular capture file", err)


if __name__ == "__main__":
    unittest.main()
