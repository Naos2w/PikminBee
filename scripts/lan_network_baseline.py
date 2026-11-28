#!/usr/bin/env python3
"""Summarise sanitized, pre-collected LAN request observations offline."""
import argparse
import csv
import json
from pathlib import Path
import re

from process_resource_baseline import distribution, uint

FIELDS = ['request_id', 'kind', 'start_ms', 'end_ms', 'outcome', 'status',
          'request_body_bytes', 'response_body_bytes']
KINDS = ('poll', 'other')
OUTCOMES = ('http', 'timeout', 'transport_error', 'cancelled', 'incomplete')


def summarise(stream, duration_ms):
    if type(duration_ms) is not int or not 0 < duration_ms < 2**63:
        raise ValueError('Positive capture duration required')
    reader = csv.reader(stream, strict=True)
    if next(reader, None) != FIELDS:
        raise ValueError('Unexpected CSV header')
    seen = set()
    groups = {kind: [] for kind in KINDS}
    for row in reader:
        if len(row) != len(FIELDS):
            raise ValueError('Malformed request row')
        ident, kind, start, end, outcome, status, sent, received = row
        ident, start, end = map(uint, (ident, start, end))
        if ident in seen or kind not in KINDS or outcome not in OUTCOMES:
            raise ValueError('Duplicate request or invalid category')
        seen.add(ident)
        if not 0 <= start < duration_ms or not start <= end <= duration_ms:
            raise ValueError('Request outside capture window')
        if outcome == 'incomplete' and end != duration_ms:
            raise ValueError('Incomplete request must end at capture boundary')
        if outcome == 'http':
            code = uint(status)
            if not 200 <= code <= 599:
                raise ValueError('Invalid final HTTP status')
        else:
            if status:
                raise ValueError('Non-HTTP outcome must not have a status')
            code = None
        # Blank means unknown, not zero. Do not invent byte counts.
        body_sizes = [None if field == '' else uint(field) for field in (sent, received)]
        groups[kind].append((start, end, outcome, code, *body_sizes))

    def aggregate(rows):
        counts = {outcome: 0 for outcome in OUTCOMES}
        statuses = {str(n) + 'xx': 0 for n in range(2, 6)}
        successful_latency = []
        starts = []
        totals = [0, 0]
        missing = [0, 0]
        for start, end, outcome, code, sent, received in rows:
            starts.append(start)
            counts[outcome] += 1
            if code is not None:
                statuses[str(code // 100) + 'xx'] += 1
                if 200 <= code < 300:
                    successful_latency.append(end - start)
            for i, value in enumerate((sent, received)):
                if value is None:
                    missing[i] += 1
                else:
                    totals[i] += value
        starts.sort()
        gaps = [b - a for a, b in zip(starts, starts[1:])]
        return {
            'request_count': len(rows),
            'request_starts_per_minute': 60_000 * len(rows) / duration_ms,
            'outcomes': counts, 'http_status_classes': statuses,
            'successful_2xx_latency_ms': distribution(successful_latency) if successful_latency else None,
            'request_start_gap_ms': distribution(gaps) if gaps else None,
            'request_body_bytes_known_sum': totals[0],
            'response_body_bytes_known_sum': totals[1],
            'request_body_bytes_unknown_count': missing[0],
            'response_body_bytes_unknown_count': missing[1],
            'body_byte_totals_complete': not any(missing) and not counts['incomplete'],
        }

    return {'duration_ms': duration_ms, 'five_minute_window_met': duration_ms >= 300_000,
            'all': aggregate(groups['poll'] + groups['other']),
            'poll': aggregate(groups['poll']), 'other': aggregate(groups['other']),
            'apk_provenance_verified': False, 'device_conditions_verified': False,
            'capture_provenance_verified': False, 'acceptance_verified': False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('--duration-ms', required=True, type=int)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--apk-sha256', required=True)
    parser.add_argument('--abi', required=True, choices=['arm64-v8a', 'x86_64', 'armeabi-v7a', 'x86'])
    parser.add_argument('--variant', required=True, choices=['baseline', 'optimized'])
    parser.add_argument('--environment', required=True, choices=['device', 'emulator', 'synthetic'])
    parser.add_argument('--scenario', required=True, choices=['idle', 'foreground'])
    args = parser.parse_args(argv)
    if not re.fullmatch(r'[a-fA-F0-9]{40}', args.source_sha) or not re.fullmatch(r'[a-fA-F0-9]{64}', args.apk_sha256):
        parser.error('Full source SHA and APK SHA-256 required')
    try:
        if not args.capture.is_file():
            raise ValueError('Capture must be a regular file')
        with args.capture.open(encoding='utf-8', newline='') as stream:
            result = summarise(stream, args.duration_ms)
    except (OSError, ValueError, csv.Error) as error:
        parser.error(str(error))
    result.update(source_sha=args.source_sha.lower(), apk_sha256=args.apk_sha256.lower(),
                  abi=args.abi, variant=args.variant, environment=args.environment,
                  scenario=args.scenario)
    print(json.dumps(result, indent=2, sort_keys=True, allow_nan=False))


if __name__ == '__main__':
    main()
