import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest

from process_resource_baseline import FIELDS, main, summarise

PACKAGE = 'com.naos.pikminbee'


def fixture(rows):
    return io.StringIO(','.join(FIELDS) + '\n' + '\n'.join(','.join(map(str, row)) for row in rows))


def row(t=0, ticks=0, pss=100, rss=200, pid=123, start=1, package=PACKAGE):
    return [t, package, pid, start, ticks, pss, rss]


def analyse(rows, hz=100, gap=10000):
    return summarise(fixture(rows), PACKAGE, hz, gap)


class ResourceTest(unittest.TestCase):
    def test_weighted_cpu_unequal_intervals(self):
        report = analyse([row(), row(1000, 100), row(4000, 100)])
        self.assertEqual(25, report['cpu_time_weighted_one_core_pct'])
        self.assertEqual(50, report['cpu_one_core_pct']['median'])
        self.assertEqual(100, report['cpu_one_core_pct']['p95'])

    def test_multicore_not_clamped(self):
        self.assertEqual(250, analyse([row(), row(1000, 250)])['cpu_one_core_pct']['max'])

    def test_clock_hz(self):
        self.assertEqual(50, analyse([row(), row(1000, 125)], hz=250)['cpu_time_weighted_one_core_pct'])

    def test_memory_summary(self):
        report = analyse([row(), row(1000, 10, 300, 400), row(2000, 20, 200, 800)])
        self.assertEqual({'min': 100, 'median': 200, 'p95': 300, 'max': 300}, report['pss_kib'])
        self.assertEqual(800, report['rss_kib']['max'])

    def test_coverage(self):
        self.assertFalse(analyse([row(), row(1000, 10)])['five_minute_window_met'])
        self.assertTrue(analyse([row(i*1000, i*10) for i in range(301)])['five_minute_window_met'])

    def test_identity_changes(self):
        for changed in [row(1000, pid=124), row(1000, start=2), row(1000, package='other')]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                analyse([row(), changed])

    def test_time_and_counter_reversal(self):
        for changed in [row(1000, 0), row(0, 10), row(10001, 10)]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                analyse([row(t=0, ticks=10), changed])

    def test_bad_fields(self):
        for changed in [row(1000, pss=-1), row(1000, rss='NaN'), row(1000, pid=0), row(1000)[:-1], row(1000, ticks=2**63)]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                analyse([row(), changed])

    def test_missing_data(self):
        for rows in [[], [row()]]:
            with self.assertRaises(ValueError):
                analyse(rows)
        with self.assertRaises(ValueError):
            summarise(io.StringIO('wrong,header\n'), PACKAGE, 100, 1000)

    def test_invalid_configuration(self):
        for hz, gap in [(0, 1000), (100, 0), (True, 1000)]:
            with self.assertRaises(ValueError):
                analyse([row(), row(1000)], hz, gap)

    def test_cli_metadata_and_privacy(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'private_capture.csv'
            path.write_text(fixture([row(), row(1000, 10)]).getvalue(), encoding='utf-8')
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                main([str(path), '--package', PACKAGE, '--source-sha', 'A'*40,
                      '--apk-sha256', 'B'*64, '--abi', 'arm64-v8a', '--variant', 'baseline',
                      '--environment', 'synthetic', '--scenario', 'idle', '--clock-hz', '100', '--max-gap-ms', '2000'])
            report = json.loads(output.getvalue())
            self.assertEqual('a'*40, report['source_sha'])
            self.assertEqual('b'*64, report['apk_sha256'])
            self.assertEqual('synthetic', report['environment'])
            self.assertFalse(report['acceptance_verified'])
            for private in ('pid', 'start_ticks', 'elapsed_ms', 'private_capture.csv'):
                self.assertNotIn(private, output.getvalue())

    def test_no_counter_reset_as_idle(self):
        with self.assertRaises(ValueError):
            analyse([row(0, 100), row(1000, 99), row(2000, 101)])


if __name__ == '__main__':
    unittest.main()
