#!/usr/bin/env python3
"""Compare APK experiments by declared source SHA and exact native ABI."""
import argparse
import json
from pathlib import Path
import re

from apk_size_audit import audit, SUPPORTED_ABIS


def compare(baseline, candidate):
    sha = baseline["source_sha"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or sha != candidate["source_sha"]:
        raise ValueError("Both reports require the same full source SHA")
    if baseline["abis"] != candidate["abis"] or not baseline["abis"]:
        raise ValueError("Both reports require the same nonempty ABI set")
    if not baseline["dex_count"] or not candidate["dex_count"]:
        raise ValueError("Both APKs must contain DEX")
    if baseline["apk_sha256"] == candidate["apk_sha256"]:
        raise ValueError("Identical APKs are not an optimization experiment")
    before = baseline["apk_bytes"]
    after = candidate["apk_bytes"]
    if before <= 0 or after <= 0:
        raise ValueError("APK sizes must be positive")
    categories = sorted(set(baseline["category_bytes"]) |
                        set(candidate["category_bytes"]))
    deltas = {
        category: candidate["category_bytes"].get(category, 0)
        - baseline["category_bytes"].get(category, 0)
        for category in categories
    }
    overhead = candidate["archive_overhead_bytes"] - baseline["archive_overhead_bytes"]
    if sum(deltas.values()) + overhead != after - before:
        raise ValueError("Category deltas do not reconcile with APK sizes")
    # Incremental APK packaging can leave large unused ZIP regions. Report
    # the actual file size, but flag suspicious overhead rather than claiming
    # the category reduction alone is a download-size improvement.
    warnings = []
    for label, report in (("baseline", baseline), ("candidate", candidate)):
        archive_overhead = report["archive_overhead_bytes"]
        if archive_overhead > 1024 * 1024 and archive_overhead * 20 > report["apk_bytes"]:
            warnings.append(f"{label}: unusually large ZIP overhead; repeat with clean packaging")
    return {
        "packaging_warnings": warnings,
        "source_sha": sha,
        "abis": baseline["abis"],
        "baseline": baseline,
        "candidate": candidate,
        "delta_bytes": after - before,
        "reduction_percent": round((before - after) * 100 / before, 4),
        "category_delta_bytes": deltas,
        "archive_overhead_delta_bytes": overhead,
        "dex_count_delta": candidate["dex_count"] - baseline["dex_count"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--baseline-source-sha", required=True)
    parser.add_argument("--candidate-source-sha", required=True)
    parser.add_argument("--abi", required=True, choices=sorted(SUPPORTED_ABIS))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        result = compare(
            audit(args.baseline, args.baseline_source_sha, args.abi),
            audit(args.candidate, args.candidate_source_sha, args.abi),
        )
    except ValueError as error:
        parser.error(str(error))
    report = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    print(report, end="")


if __name__ == "__main__":
    main()
