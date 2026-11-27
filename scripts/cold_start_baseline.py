"""Summarise explicitly captured Android cold-start timings; never operate a device."""
import argparse
import json
import math
import re
import statistics
import sys
from pathlib import Path

PACKAGES = {"com.naos.pikminbee", "com.naos.pikminbee.uat"}


def parse_sample(text, package):
    # am start -W output varies by Android version. Missing launch state is
    # not evidence of a cold launch; reject rather than silently mixing it.
    fields = {}
    for line in text.splitlines():
        match = re.fullmatch(r"\s*(Status|LaunchState|Activity|TotalTime):\s*(.*?)\s*", line)
        if match:
            key, value = match.groups()
            if key in fields:
                raise ValueError(f"duplicate {key}")
            fields[key] = value
    if fields.get("Status") != "ok" or fields.get("LaunchState") != "COLD":
        raise ValueError("not a confirmed successful cold launch")
    activity = fields.get("Activity", "")
    if activity.partition("/")[0] != package or not activity.partition("/")[2]:
        raise ValueError("unexpected or missing activity")
    value = fields.get("TotalTime", "")
    if not re.fullmatch(r"[0-9]+", value) or int(value) <= 0:
        raise ValueError("missing or invalid TotalTime")
    return int(value)


def summarise(samples):
    if not samples or any(type(x) is not int or x <= 0 for x in samples):
        raise ValueError("positive integer millisecond samples required")
    ordered = sorted(samples)
    return {
        "count": len(ordered),
        "min_ms": ordered[0],
        "median_ms": statistics.median(ordered),
        "p95_ms": ordered[math.ceil(0.95 * len(ordered)) - 1],
        "max_ms": ordered[-1],
        "samples_ms": samples,
        "minimum_20_samples_met": len(ordered) >= 20,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", required=True, choices=sorted(PACKAGES))
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--variant", required=True, choices=["baseline", "optimized"])
    parser.add_argument("--environment", required=True, choices=["device", "emulator", "synthetic"])
    parser.add_argument("--abi", required=True, choices=["arm64-v8a", "x86_64", "armeabi-v7a", "x86"])
    parser.add_argument("files", nargs="+", type=Path, help="one am start -W capture per launch")
    args = parser.parse_args(argv)
    if not re.fullmatch(r"[0-9a-fA-F]{40}", args.source_sha):
        parser.error("source SHA must contain 40 hexadecimal characters")
    try:
        paths = [path.resolve(strict=True) for path in args.files]
        if len(set(paths)) != len(paths):
            raise ValueError("duplicate capture path")
        samples = []
        for index, path in enumerate(paths, 1):
            try:
                samples.append(parse_sample(path.read_text(encoding="utf-8-sig"), args.package))
            except (OSError, UnicodeError, ValueError) as exc:
                raise ValueError(f"capture {index}: {exc.__class__.__name__}: invalid capture") from exc
        result = summarise(samples)
    except (OSError, ValueError) as exc:
        print(f"Cannot produce baseline: {exc}", file=sys.stderr)
        return 1
    print(json.dumps({
        "metric": "am_start_total_time_ms",
        "percentile_method": "nearest_rank",
        "source_sha": args.source_sha.lower(),
        "package": args.package,
        "variant": args.variant,
        "environment": args.environment,
        "abi": args.abi,
        **result,
    }, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
