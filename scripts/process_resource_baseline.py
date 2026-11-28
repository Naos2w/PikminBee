#!/usr/bin/env python3
"""Analyse explicitly collected per-process CPU and memory samples offline."""
import argparse
import csv
import json
import math
from pathlib import Path
import re
import statistics

FIELDS = ['elapsed_ms', 'package', 'pid', 'start_ticks', 'cpu_ticks', 'pss_kib', 'rss_kib']
PACKAGES = ('com.naos.pikminbee', 'com.naos.pikminbee.uat')


def uint(value):
    if not isinstance(value, str) or not re.fullmatch(r'[0-9]{1,19}', value):
        raise ValueError('Expected an unsigned integer field')
    n = int(value)
    if n >= 2**63:
        raise ValueError('Integer field out of range')
    return n


def distribution(values):
    ordered = sorted(values)
    return {'min': ordered[0], 'median': statistics.median(ordered),
            'p95': ordered[math.ceil(.95 * len(ordered)) - 1], 'max': ordered[-1]}


def summarise(stream, package, clock_hz, max_gap_ms):
    if package not in PACKAGES or type(clock_hz) is not int or clock_hz <= 0:
        raise ValueError('Invalid package or clock tick frequency')
    if type(max_gap_ms) is not int or max_gap_ms <= 0:
        raise ValueError('Positive maximum sample gap required')
    reader = csv.reader(stream, strict=True)
    if next(reader, None) != FIELDS:
        raise ValueError('Unexpected CSV header')
    identity = previous = None
    cpu = []
    pss, rss, gaps = [], [], []
    total_ticks = total_ms = 0
    for row in reader:
        if len(row) != len(FIELDS) or row[1] != package:
            raise ValueError('Malformed sample or unexpected package')
        elapsed, pid, start, ticks, pss_kib, rss_kib = map(uint, row[:1] + row[2:])
        if not pid:
            raise ValueError('Invalid PID')
        if identity is None:
            identity = (pid, start)
        elif identity != (pid, start):
            raise ValueError('Process changed during capture')
        if previous is not None:
            gap = elapsed - previous[0]
            delta = ticks - previous[1]
            if not 0 < gap <= max_gap_ms or delta < 0:
                raise ValueError('Invalid interval, counter reset or excessive sample gap')
            gaps.append(gap)
            cpu.append(100_000 * delta / (clock_hz * gap))
            total_ticks += delta
            total_ms += gap
        previous = elapsed, ticks
        pss.append(pss_kib)
        rss.append(rss_kib)
    if not cpu:
        raise ValueError('At least two samples required')
    return {
        'sample_count': len(pss), 'interval_count': len(cpu), 'duration_ms': total_ms,
        'sample_gap_ms': distribution(gaps), 'max_allowed_gap_ms': max_gap_ms,
        'clock_ticks_per_second': clock_hz,
        'cpu_one_core_pct': distribution(cpu),
        'cpu_time_weighted_one_core_pct': 100_000 * total_ticks / (clock_hz * total_ms),
        'pss_kib': distribution(pss), 'rss_kib': distribution(rss),
        'five_minute_window_met': total_ms >= 300_000,
        'apk_provenance_verified': False, 'device_conditions_verified': False,
        'capture_provenance_verified': False, 'acceptance_verified': False,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('--package', required=True, choices=PACKAGES)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--apk-sha256', required=True)
    parser.add_argument('--abi', required=True, choices=['arm64-v8a', 'x86_64', 'armeabi-v7a', 'x86'])
    parser.add_argument('--variant', required=True, choices=['baseline', 'optimized'])
    parser.add_argument('--environment', required=True, choices=['device', 'emulator', 'synthetic'])
    parser.add_argument('--scenario', required=True, choices=['idle', 'foreground'])
    parser.add_argument('--clock-hz', required=True, type=int)
    parser.add_argument('--max-gap-ms', required=True, type=int)
    args = parser.parse_args(argv)
    if not re.fullmatch(r'[a-fA-F0-9]{40}', args.source_sha) or not re.fullmatch(r'[a-fA-F0-9]{64}', args.apk_sha256):
        parser.error('Full source SHA and APK SHA-256 required')
    try:
        if not args.capture.is_file():
            raise ValueError('Capture must be a regular file')
        with args.capture.open(encoding='utf-8', newline='') as stream:
            result = summarise(stream, args.package, args.clock_hz, args.max_gap_ms)
    except (OSError, ValueError, csv.Error) as error:
        parser.error(str(error))
    result.update(package=args.package, source_sha=args.source_sha.lower(),
                  apk_sha256=args.apk_sha256.lower(), abi=args.abi, variant=args.variant,
                  environment=args.environment, scenario=args.scenario)
    print(json.dumps(result, indent=2, sort_keys=True, allow_nan=False))


if __name__ == '__main__':
    main()
