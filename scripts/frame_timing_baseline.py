#!/usr/bin/env python3
"""Summarise a single, previously collected Android HWUI framestats window."""
import argparse
import json
import math
from pathlib import Path
import re
import statistics

MARKER = '---PROFILEDATA---'
PROCESS = re.compile(r'^\*\* Graphics info for pid \d+ \[([^\]]+)\] \*\*$')
MAX_NS = (1 << 63) - 1


def profile_sections(text, package):
    sections = []
    active = None
    owner = None
    for raw in text.splitlines():
        line = raw.strip()
        process = PROCESS.fullmatch(line)
        if process:
            if active is not None:
                raise ValueError('Unclosed profile section')
            owner = process.group(1)
        elif line == MARKER:
            if active is None:
                if owner != package:
                    raise ValueError('Unexpected or unidentified process in capture')
                active = []
            else:
                sections.append(active)
                active = None
        elif active is not None and line:
            active.append(line)
    if active is not None or not sections:
        raise ValueError('Missing or incomplete profile section')
    return sections


def fields(line):
    parts = line.split(',')
    if parts[-1] == '':
        parts.pop()
    return parts


def integer(value):
    if not re.fullmatch(r'[0-9]{1,19}', value):
        raise ValueError('Invalid frame field')
    result = int(value)
    if result >= MAX_NS:
        raise ValueError('Invalid or sentinel frame field')
    return result


def summarise(text, package, section=None):
    sections = profile_sections(text, package)
    if section is None and len(sections) != 1:
        raise ValueError('Multiple windows: select one explicitly with --section')
    selected = 0 if section is None else section
    if not 0 <= selected < len(sections):
        raise ValueError('Section index out of range')
    lines = sections[selected]
    if not lines:
        raise ValueError('Empty profile section')
    header = fields(lines[0])
    required = {'Flags', 'IntendedVsync', 'FrameCompleted'}
    if len(set(header)) != len(header) or not required.issubset(header):
        raise ValueError('Missing or duplicated frame columns')
    index = {name: header.index(name) for name in required}
    deadline_idx = header.index('FrameDeadline') if 'FrameDeadline' in header else None
    seen = set()
    durations = []
    excluded = late = deadline_samples = 0
    for line in lines[1:]:
        row = fields(line)
        if len(row) != len(header):
            raise ValueError('Malformed frame row')
        if integer(row[index['Flags']]) != 0:
            excluded += 1
            continue
        start = integer(row[index['IntendedVsync']])
        end = integer(row[index['FrameCompleted']])
        if not start or end <= start:
            raise ValueError('Incomplete or invalid frame timestamps')
        if start in seen:
            raise ValueError('Duplicate intended vsync in selected window')
        seen.add(start)
        durations.append((end - start) / 1_000_000)
        if deadline_idx is not None:
            deadline = integer(row[deadline_idx])
            if deadline <= start:
                raise ValueError('Invalid frame deadline')
            deadline_samples += 1
            late += end > deadline
    if not durations:
        raise ValueError('No unflagged completed frames')
    ordered = sorted(durations)
    return {
        'metric': 'hwui_intended_vsync_to_frame_completed_ms',
        'section_index': selected, 'section_count': len(sections),
        'frames': len(ordered), 'excluded_flagged_frames': excluded,
        'median_ms': statistics.median(ordered),
        'p95_ms': ordered[math.ceil(.95 * len(ordered)) - 1],
        'min_ms': ordered[0], 'max_ms': ordered[-1],
        'deadline_samples': deadline_samples,
        'completion_after_deadline_count': late if deadline_samples else None,
        'completion_after_deadline_pct': 100 * late / deadline_samples if deadline_samples else None,
        'device_conditions_verified': False,
        'apk_provenance_verified': False,
        'map_surface_rendering_verified': False,
        'acceptance_verified': False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--apk-sha256', required=True)
    parser.add_argument('--abi', required=True, choices=['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'])
    parser.add_argument('--variant', required=True, choices=['baseline', 'optimized'])
    parser.add_argument('--environment', required=True, choices=['device', 'emulator', 'synthetic'])
    parser.add_argument('--section', type=int, help='zero-based window index; required for multiple windows')
    parser.add_argument('capture', type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r'[0-9a-fA-F]{40}', args.source_sha) or not re.fullmatch(r'[0-9a-fA-F]{64}', args.apk_sha256):
        parser.error('Expected full source SHA and APK SHA-256')
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+', args.package):
        parser.error('Invalid package name')
    try:
        if not args.capture.is_file():
            raise ValueError('Expected a regular capture file')
        result = summarise(args.capture.read_text(encoding='utf-8-sig'), args.package, args.section)
    except (OSError, ValueError) as exc:
        # Do not include filenames or raw capture content in output.
        parser.error(str(exc) if isinstance(exc, ValueError) and not isinstance(exc, UnicodeError) else 'Cannot read capture')
    result.update(package=args.package, source_sha=args.source_sha.lower(),
                  apk_sha256=args.apk_sha256.lower(), abi=args.abi,
                  variant=args.variant, environment=args.environment)
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == '__main__':
    main()
