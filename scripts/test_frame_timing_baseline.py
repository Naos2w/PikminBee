import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from frame_timing_baseline import summarise

PKG = 'com.naos.pikminbee'
HEADER = 'Flags,IntendedVsync,FrameCompleted,FrameDeadline,'
MARK = '---PROFILEDATA---'


def capture(rows, header=HEADER, package=PKG, sections=1):
    block = '\n'.join([MARK, header, *rows, MARK])
    return '\n'.join([f'** Graphics info for pid 123 [{package}] **', *([block] * sections)])


class FrameTimingTests(unittest.TestCase):
    def test_summary_and_flagged_exclusion(self):
        result = summarise(capture(['0,1000000,11000000,11000000,',
                                    '0,20000000,40000000,30000000,',
                                    '1,0,0,0,']), PKG)
        self.assertEqual(result['frames'], 2)
        self.assertEqual(result['median_ms'], 15)
        self.assertEqual(result['p95_ms'], 20)
        self.assertEqual(result['excluded_flagged_frames'], 1)
        self.assertEqual(result['completion_after_deadline_pct'], 50)
        self.assertFalse(result['map_surface_rendering_verified'])

    def test_legacy_without_deadline(self):
        result = summarise(capture(['0,1,10000001,'], 'Flags,IntendedVsync,FrameCompleted,'), PKG)
        self.assertEqual(result['median_ms'], 10)
        self.assertIsNone(result['completion_after_deadline_pct'])

    def test_column_order_and_extra_fields(self):
        result = summarise(capture(['10000001,5,0,1'], 'FrameCompleted,Vsync,Flags,IntendedVsync'), PKG)
        self.assertEqual(result['min_ms'], 10)

    def test_multiple_windows_need_selection(self):
        raw = capture(['0,1,2,3,'], sections=2)
        with self.assertRaisesRegex(ValueError, 'Multiple windows'):
            summarise(raw, PKG)
        self.assertEqual(summarise(raw, PKG, 1)['section_index'], 1)
        with self.assertRaisesRegex(ValueError, 'out of range'):
            summarise(raw, PKG, 2)

    def test_wrong_process(self):
        with self.assertRaisesRegex(ValueError, 'process'):
            summarise(capture(['0,1,2,3,'], package='example.other'), PKG)

    def test_unclosed_profile(self):
        with self.assertRaisesRegex(ValueError, 'incomplete'):
            summarise(capture(['0,1,2,3,']).removesuffix(MARK), PKG)

    def test_duplicate_frames(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            summarise(capture(['0,1,2,3,', '0,1,3,4,']), PKG)

    def test_bad_rows(self):
        for row in ['0,1,2,', '0,0,2,3,', '0,3,2,4,', '0,1,9223372036854775807,3,', '0,1,2,1,']:
            with self.subTest(row=row), self.assertRaises(ValueError):
                summarise(capture([row]), PKG)

    def test_missing_and_duplicate_columns(self):
        for header in ['Flags,IntendedVsync,', 'Flags,IntendedVsync,FrameCompleted,Flags,']:
            with self.subTest(header=header), self.assertRaisesRegex(ValueError, 'columns'):
                summarise(capture([], header), PKG)

    def test_no_valid_frames(self):
        with self.assertRaisesRegex(ValueError, 'No unflagged'):
            summarise(capture(['1,0,0,0,']), PKG)

    def test_nearest_rank(self):
        rows = [f'0,{i*100000000},{i*101000000},{i*100000000+30000000},' for i in range(1,21)]
        result = summarise(capture(rows), PKG)
        self.assertEqual(result['p95_ms'], 19)
        self.assertEqual(result['median_ms'], 10.5)

    def test_cli_privacy_and_provenance(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'private-capture.txt'
            path.write_text(capture(['0,1,2,3,']), encoding='utf-8')
            cmd = [sys.executable, str(Path(__file__).with_name('frame_timing_baseline.py')),
                   '--package', PKG, '--source-sha', 'A'*40, '--apk-sha256', 'B'*64,
                   '--abi', 'arm64-v8a', '--variant', 'baseline', '--environment', 'synthetic', str(path)]
            process = subprocess.run(cmd, capture_output=True, text=True, check=True)
            result = json.loads(process.stdout)
            self.assertEqual(result['source_sha'], 'a'*40)
            self.assertEqual(result['apk_sha256'], 'b'*64)
            self.assertFalse(result['apk_provenance_verified'])
            self.assertFalse(result['acceptance_verified'])
            self.assertNotIn(str(path), process.stdout)
            self.assertNotIn('pid 123', process.stdout)
            cmd[cmd.index('--apk-sha256')+1] = 'invalid'
            self.assertNotEqual(subprocess.run(cmd, capture_output=True).returncode, 0)


if __name__ == '__main__':
    unittest.main()
