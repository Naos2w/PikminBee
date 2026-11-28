"""Compare two offline cold-start reports, without operating a device (#160)."""
import argparse
import json
import re
import sys
from pathlib import Path

from cold_start_baseline import PACKAGES, summarise


MATCH_FIELDS = {
    "metric": {"am_start_total_time_ms"},
    "percentile_method": {"nearest_rank"},
    "package": PACKAGES,
    "environment": {"device", "emulator", "synthetic"},
    "abi": {"arm64-v8a", "x86_64", "armeabi-v7a", "x86"},
}


def validate(report, variant):
    if not isinstance(report, dict) or report.get("variant") != variant:
        raise ValueError(f"expected {variant} report")
    for field, allowed in MATCH_FIELDS.items():
        value = report.get(field)
        if not isinstance(value, str) or value not in allowed:
            raise ValueError(f"invalid {variant} {field}")
    sha = report.get("source_sha")
    if not isinstance(sha, str) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError(f"invalid {variant} source_sha")
    apk_hash = report.get("apk_sha256")
    if not isinstance(apk_hash, str) or not re.fullmatch(r"[0-9a-f]{64}", apk_hash):
        raise ValueError(f"missing or invalid {variant} apk_sha256")
    samples = report.get("samples_ms")
    if not isinstance(samples, list):
        raise ValueError(f"missing {variant} samples_ms")
    stats = summarise(samples)
    if not stats["minimum_20_samples_met"]:
        raise ValueError(f"{variant} requires at least 20 samples")
    # Reject stale, manually edited or internally inconsistent summary fields.
    # JSON booleans compare equal to integers in Python, so check types too.
    for field, expected in stats.items():
        actual = report.get(field)
        if field == "median_ms":
            valid_type = type(actual) in (int, float)
        else:
            valid_type = type(actual) is type(expected)
        if not valid_type or actual != expected:
            raise ValueError(f"inconsistent {variant} {field}")
    return stats


def compare(baseline, optimized):
    first = validate(baseline, "baseline")
    second = validate(optimized, "optimized")
    for field in (*MATCH_FIELDS, "source_sha"):
        if baseline[field] != optimized[field]:
            raise ValueError(f"reports differ in {field}")
    if baseline["apk_sha256"] == optimized["apk_sha256"]:
        raise ValueError("identical APK hashes are not an optimization comparison")
    comparison = {}
    for field in ("min_ms", "median_ms", "p95_ms", "max_ms"):
        before, after = first[field], second[field]
        comparison[field] = {
            "baseline": before,
            "optimized": after,
            "delta_ms": after - before,
            "delta_percent": round((after - before) / before * 100, 3),
        }
    return {
        **{field: baseline[field] for field in (*MATCH_FIELDS, "source_sha")},
        "baseline_apk_sha256": baseline["apk_sha256"],
        "optimized_apk_sha256": optimized["apk_sha256"],
        "baseline_count": first["count"],
        "optimized_count": second["count"],
        "comparison": comparison,
        "interpretation": "descriptive_only",
        "device_conditions_verified": False,
        "apk_provenance_verified": False,
        "acceptance_verified": False,
    }


def unique_fields(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON field")
        result[key] = value
    return result


def reject_constant(_):
    raise ValueError("non-finite JSON constant")


def read_report(path):
    if not path.is_file():
        raise ValueError("report must be a regular file")
    return json.loads(path.read_text(encoding="utf-8-sig"),
                      object_pairs_hook=unique_fields,
                      parse_constant=reject_constant)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("optimized", type=Path)
    args = parser.parse_args(argv)
    try:
        result = compare(read_report(args.baseline), read_report(args.optimized))
    except (OSError, UnicodeError, ValueError, OverflowError) as exc:
        print(f"Cannot compare reports: {exc.__class__.__name__}: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, allow_nan=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
