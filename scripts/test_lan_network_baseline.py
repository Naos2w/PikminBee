import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from lan_network_baseline import FIELDS, summarise


class LanNetworkBaselineTest(unittest.TestCase):
    def read(self, *rows, duration=300_000):
        return summarise(io.StringIO(','.join(FIELDS) + '\n' + '\n'.join(rows)), duration)

    def test_poll_rates_use_entire_window(self):
        r = self.read('1,poll,100,150,http,200,10,20', '2,poll,1000,1080,http,200,10,40',
                      '3,other,1200,1210,http,204,0,0')
        self.assertEqual(r['poll']['request_starts_per_minute'], .4)
        self.assertEqual(r['all']['request_starts_per_minute'], .6)
        self.assertEqual(r['poll']['request_start_gap_ms']['median'], 900)
        self.assertEqual(r['poll']['successful_2xx_latency_ms']['median'], 65)
        self.assertEqual(r['poll']['response_body_bytes_known_sum'], 60)

    def test_failed_and_incomplete_not_success_latency(self):
        r = self.read('1,poll,0,200,http,503,0,20', '2,poll,200,400,timeout,,0,',
                      '3,poll,500,520,http,200,0,10', '4,poll,299000,300000,incomplete,,0,')
        self.assertEqual(r['poll']['successful_2xx_latency_ms']['p95'], 20)
        self.assertEqual(r['poll']['http_status_classes']['5xx'], 1)
        self.assertEqual(r['poll']['outcomes']['timeout'], 1)
        self.assertEqual(r['poll']['outcomes']['incomplete'], 1)
        self.assertFalse(r['poll']['body_byte_totals_complete'])

    def test_zero_observations(self):
        r = self.read()
        self.assertEqual(r['all']['request_count'], 0)
        self.assertIsNone(r['all']['successful_2xx_latency_ms'])
        self.assertFalse(r['capture_provenance_verified'])

    def test_overlapping_distinct_requests(self):
        r = self.read('1,poll,0,90,http,200,0,1', '2,poll,0,20,http,200,0,1')
        self.assertEqual(r['poll']['request_start_gap_ms']['min'], 0)

    def test_duplicate_id_rejected_across_categories(self):
        with self.assertRaises(ValueError):
            self.read('1,poll,0,1,http,200,0,1', '1,other,2,3,http,200,0,1')

    def test_unknown_bytes_distinguished_from_zero(self):
        r = self.read('1,poll,0,1,http,200,,0')['poll']
        self.assertEqual(r['request_body_bytes_unknown_count'], 1)
        self.assertEqual(r['response_body_bytes_unknown_count'], 0)
        self.assertFalse(r['body_byte_totals_complete'])

    def test_bad_window(self):
        for row in ('1,poll,10,9,http,200,0,0', '1,poll,0,300001,http,200,0,0',
                    '1,poll,300000,300000,http,200,0,0', '1,poll,0,3,incomplete,,0,0'):
            with self.subTest(row=row), self.assertRaises(ValueError):
                self.read(row)

    def test_status_outcome_validation(self):
        for row in ('1,poll,0,1,http,100,0,0', '1,poll,0,1,timeout,200,0,0',
                    '1,poll,0,1,http,,0,0', '1,poll,0,1,success,200,0,0'):
            with self.subTest(row=row), self.assertRaises(ValueError):
                self.read(row)

    def test_bad_categories_or_private_fields_rejected(self):
        for row in ('1,url,0,1,http,200,0,0', '1,poll,0,1,http,200,0,0,secret',
                    '1,poll,0,1,http,200,-1,0'):
            with self.subTest(row=row), self.assertRaises(ValueError):
                self.read(row)

    def test_duration_and_coverage(self):
        self.assertFalse(self.read(duration=299999)['five_minute_window_met'])
        with self.assertRaises(ValueError):
            self.read(duration=0)

    def test_output_does_not_contain_request_identity(self):
        result = json.dumps(self.read('99999999,poll,0,1,http,200,0,0'))
        self.assertNotIn('99999999', result)
        self.assertNotIn('request_id', result)

    def test_cli_provenance_and_privacy(self):
        with tempfile.TemporaryDirectory() as directory:
            capture = Path(directory) / "private-capture.csv"
            capture.write_text(",".join(FIELDS) + "\n987654,poll,0,5,http,200,0,1\n", encoding="utf-8")
            command = [sys.executable, str(Path(__file__).with_name("lan_network_baseline.py")),
                       str(capture), "--duration-ms", "300000", "--source-sha", "a" * 40,
                       "--apk-sha256", "b" * 64, "--abi", "arm64-v8a", "--variant",
                       "optimized", "--environment", "synthetic", "--scenario", "idle"]
            output = subprocess.check_output(command, text=True)
            result = json.loads(output)
            self.assertEqual(result["environment"], "synthetic")
            self.assertFalse(result["apk_provenance_verified"])
            self.assertNotIn("private-capture", output)
            self.assertNotIn("987654", output)
            command[command.index("--source-sha") + 1] = "short"
            self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)

    def test_header_rejected(self):
        with self.assertRaises(ValueError):
            summarise(io.StringIO('url,token\n'), 1000)


if __name__ == '__main__':
    unittest.main()
